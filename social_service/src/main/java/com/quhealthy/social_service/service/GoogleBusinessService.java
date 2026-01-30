package com.quhealthy.social_service.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quhealthy.social_service.model.SocialConnection;
import com.quhealthy.social_service.model.enums.SocialPlatform; // 👈 Tu Enum
import com.quhealthy.social_service.repository.SocialConnectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleBusinessService {

    private final SocialConnectionRepository socialRepository;
    // Usamos el WebClient.builder() por si necesitas configurar timeouts después
    private final WebClient webClient = WebClient.builder().build();
    
    @Value("${google.client.id}")
    private String clientId;

    @Value("${google.client.secret}")
    private String clientSecret;

    @Value("${application.api.url}") 
    private String apiUrl;

    private static final String REDIRECT_PATH = "/api/social/google/callback";
    private static final String SCOPE = "https://www.googleapis.com/auth/business.manage";

    /**
     * 1. Generar URL de Auth
     */
    public String generateAuthUrl(Long providerId) {
        String redirectUri = apiUrl + REDIRECT_PATH;
        // State seguro: providerId codificado
        String state = Base64.getEncoder().encodeToString(String.valueOf(providerId).getBytes());

        return "https://accounts.google.com/o/oauth2/v2/auth" +
                "?client_id=" + clientId +
                "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8) +
                "&response_type=code" +
                "&scope=" + URLEncoder.encode(SCOPE, StandardCharsets.UTF_8) +
                "&access_type=offline" + 
                "&prompt=consent" +      
                "&state=" + state;
    }

    /**
     * 2. Manejar Callback
     */
    public void handleCallback(String code, String state) {
        // A. Validar State
        String providerIdStr = new String(Base64.getDecoder().decode(state));
        Long providerId = Long.valueOf(providerIdStr);
        String redirectUri = apiUrl + REDIRECT_PATH;

        log.info("🔗 Google Callback recibido para Provider ID: {}", providerId);

        // B. Obtener Tokens
        JsonNode tokenResponse = exchangeCodeForToken(code, redirectUri);
        String accessToken = tokenResponse.get("access_token").asText();
        String refreshToken = tokenResponse.has("refresh_token") ? tokenResponse.get("refresh_token").asText() : null;
        int expiresIn = tokenResponse.get("expires_in").asInt();

        // C. Obtener Datos del Negocio (ID y Nombre)
        // Necesitamos el nombre porque tu entidad SocialConnection lo requiere (platformUserName)
        Map<String, String> locationData = fetchLocationData(accessToken);
        String locationId = locationData.get("id");   // "locations/12345"
        String locationName = locationData.get("title"); // "Clínica Dental QuHealthy"

        // D. Guardar o Actualizar en BD
        SocialConnection connection = socialRepository.findByProviderIdAndPlatform(providerId, SocialPlatform.GOOGLE_BUSINESS)
                .orElse(SocialConnection.builder()
                        .providerId(providerId)
                        .platform(SocialPlatform.GOOGLE_BUSINESS) // 👈 Usamos el Enum
                        .build());

        // Actualizamos los campos
        connection.setPlatformUserId(locationId);
        connection.setPlatformUserName(locationName); // 👈 Obligatorio en tu modelo
        connection.setAccessToken(accessToken);
        connection.setActive(true); // Reactivamos si estaba en soft delete
        
        if (refreshToken != null) {
            connection.setRefreshToken(refreshToken);
        }
        
        connection.setTokenExpiresAt(LocalDateTime.now().plusSeconds(expiresIn));

        socialRepository.save(connection);
        log.info("✅ Conexión Google Business guardada: {} ({})", locationName, locationId);
    }

    // --- Métodos Privados (WebClient) ---

    private JsonNode exchangeCodeForToken(String code, String redirectUri) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("code", code);
        formData.add("client_id", clientId);
        formData.add("client_secret", clientSecret);
        formData.add("redirect_uri", redirectUri);
        formData.add("grant_type", "authorization_code");

        return webClient.post()
                .uri("https://oauth2.googleapis.com/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(formData))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
    }

    /**
     * Obtiene el ID y el Nombre de la primera ubicación disponible.
     * @return Mapa con "id" y "title"
     */
    private Map<String, String> fetchLocationData(String accessToken) {
        try {
            // 1. Obtener Cuenta (Account)
            JsonNode accountsRes = webClient.get()
                    .uri("https://mybusinessaccountmanagement.googleapis.com/v1/accounts")
                    .headers(h -> h.setBearerAuth(accessToken))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (accountsRes == null || !accountsRes.has("accounts") || accountsRes.get("accounts").isEmpty()) {
                throw new RuntimeException("No se encontraron cuentas de Google Business.");
            }
            
            String accountName = accountsRes.get("accounts").get(0).get("name").asText(); 

            // 2. Obtener Ubicación (Location) - Pedimos el campo 'title' también
            JsonNode locationsRes = webClient.get()
                    .uri("https://mybusinessbusinessinformation.googleapis.com/v1/" + accountName + "/locations?readMask=name,title")
                    .headers(h -> h.setBearerAuth(accessToken))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (locationsRes == null || !locationsRes.has("locations") || locationsRes.get("locations").isEmpty()) {
                throw new RuntimeException("La cuenta no tiene ubicaciones verificadas.");
            }

            JsonNode firstLocation = locationsRes.get("locations").get(0);
            
            String id = firstLocation.get("name").asText(); // "locations/xxxxx"
            String title = firstLocation.has("title") ? firstLocation.get("title").asText() : "Mi Negocio";

            return Map.of("id", id, "title", title);

        } catch (Exception e) {
            log.error("❌ Error navegando API Google Business: {}", e.getMessage());
            throw new RuntimeException("Error al obtener información del negocio en Google.", e);
        }
    }
}
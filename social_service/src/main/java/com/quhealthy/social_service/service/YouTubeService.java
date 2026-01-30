package com.quhealthy.social_service.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.quhealthy.social_service.model.SocialConnection;
import com.quhealthy.social_service.model.enums.SocialPlatform;
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
public class YouTubeService {

    private final SocialConnectionRepository socialRepository;
    private final WebClient webClient = WebClient.builder().build();

    @Value("${google.client.id}")
    private String clientId;

    @Value("${google.client.secret}")
    private String clientSecret;

    @Value("${application.api.url}")
    private String apiUrl;

    private static final String REDIRECT_PATH = "/api/social/youtube/callback";
    
    // Scopes: Subir videos y leer datos del canal (readonly)
    private static final String SCOPE = "https://www.googleapis.com/auth/youtube.upload https://www.googleapis.com/auth/youtube.readonly";

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
        // A. Decodificar State
        String providerIdStr = new String(Base64.getDecoder().decode(state));
        Long providerId = Long.valueOf(providerIdStr);
        String redirectUri = apiUrl + REDIRECT_PATH;

        log.info("📹 Procesando YouTube Callback para Provider ID: {}", providerId);

        // B. Obtener Tokens
        JsonNode tokenResponse = exchangeCodeForToken(code, redirectUri);
        String accessToken = tokenResponse.get("access_token").asText();
        String refreshToken = tokenResponse.has("refresh_token") ? tokenResponse.get("refresh_token").asText() : null;
        int expiresIn = tokenResponse.get("expires_in").asInt();

        // C. Obtener Detalles del Canal (ID y Nombre)
        // Esto reemplaza el placeholder que tenías en Node.js
        Map<String, String> channelInfo = fetchChannelDetails(accessToken);
        String channelId = channelInfo.get("id");
        String channelTitle = channelInfo.get("title");

        // D. Guardar en BD (Upsert)
        SocialConnection connection = socialRepository.findByProviderIdAndPlatform(providerId, SocialPlatform.YOUTUBE)
                .orElse(SocialConnection.builder()
                        .providerId(providerId)
                        .platform(SocialPlatform.YOUTUBE)
                        .build());

        connection.setPlatformUserId(channelId);      // Ej: "UC_x5XG1OV2P6uZZ5FSM9Ttw"
        connection.setPlatformUserName(channelTitle); // Ej: "Canal de Salud QuHealthy"
        connection.setAccessToken(accessToken);
        connection.setActive(true); // Reactivamos si estaba inactivo
        
        if (refreshToken != null) {
            connection.setRefreshToken(refreshToken);
        }
        
        connection.setTokenExpiresAt(LocalDateTime.now().plusSeconds(expiresIn));

        socialRepository.save(connection);
        log.info("✅ Conexión YouTube guardada: {} ({})", channelTitle, channelId);
    }

    // --- Métodos Privados ---

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

    private Map<String, String> fetchChannelDetails(String accessToken) {
        try {
            // Llamamos a la YouTube Data API v3
            // part=snippet,id & mine=true (trae el canal del usuario autenticado)
            JsonNode response = webClient.get()
                    .uri("https://www.googleapis.com/youtube/v3/channels?part=snippet,id&mine=true")
                    .headers(h -> h.setBearerAuth(accessToken))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response == null || !response.has("items") || response.get("items").isEmpty()) {
                throw new RuntimeException("No se encontró ningún canal de YouTube asociado a esta cuenta.");
            }

            JsonNode channel = response.get("items").get(0);
            String id = channel.get("id").asText();
            String title = channel.get("snippet").get("title").asText();

            return Map.of("id", id, "title", title);

        } catch (Exception e) {
            log.error("❌ Error obteniendo canal de YouTube: {}", e.getMessage());
            throw new RuntimeException("Error al obtener información del canal de YouTube.", e);
        }
    }
}
package com.quhealthy.social_service.service.publishing;

import com.fasterxml.jackson.databind.JsonNode;
import com.quhealthy.social_service.model.SocialConnection;
import com.quhealthy.social_service.model.enums.SocialPlatform;
import com.quhealthy.social_service.repository.SocialConnectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SocialAuthService {

    private final SocialConnectionRepository connectionRepository;
    private final WebClient webClient;

    @Value("${facebook.app-id}")
    private String fbAppId;
    @Value("${facebook.app-secret}")
    private String fbAppSecret;
    @Value("${facebook.redirect-uri}")
    private String fbRedirectUri;

    /**
     * Maneja el Callback de Facebook/Instagram.
     * Intercambia code -> token -> user info -> Save DB.
     */
    @Transactional
    public void handleFacebookCallback(Long providerId, String code) {
        log.info("🔄 Procesando vinculación de Facebook para Provider: {}", providerId);

        // 1. Obtener Access Token
        String tokenUrl = String.format(
            "https://graph.facebook.com/v19.0/oauth/access_token?client_id=%s&redirect_uri=%s&client_secret=%s&code=%s",
            fbAppId, fbRedirectUri, fbAppSecret, code
        );

        JsonNode tokenResponse = webClient.get()
                .uri(tokenUrl)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block(); // Bloqueamos porque es un proceso transaccional síncrono

        if (tokenResponse == null || !tokenResponse.has("access_token")) {
            throw new RuntimeException("Error obteniendo token de Facebook");
        }

        String accessToken = tokenResponse.get("access_token").asText();

        // 2. Obtener Info del Usuario (ID y Nombre)
        String meUrl = "https://graph.facebook.com/me?fields=id,name,picture&access_token=" + accessToken;
        JsonNode meResponse = webClient.get()
                .uri(meUrl)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        String platformUserId = meResponse.get("id").asText();
        String name = meResponse.get("name").asText();
        String pictureUrl = meResponse.has("picture") ? 
                meResponse.get("picture").get("data").get("url").asText() : null;

        // 3. Guardar o Actualizar en BD
        saveConnection(providerId, SocialPlatform.FACEBOOK, platformUserId, name, accessToken, null, pictureUrl);
    }

    // Helper para guardar
    private void saveConnection(Long providerId, SocialPlatform platform, String platformUserId, 
                                String name, String accessToken, String refreshToken, String picUrl) {
        
        Optional<SocialConnection> existing = connectionRepository
                .findByProviderIdAndPlatform(providerId, platform);

        SocialConnection connection;
        if (existing.isPresent()) {
            connection = existing.get();
            // Si ya existe, actualizamos tokens
            connection.setAccessToken(accessToken);
            if (refreshToken != null) connection.setRefreshToken(refreshToken);
            connection.setPlatformUserName(name);
            connection.setUpdatedAt(LocalDateTime.now());
            log.info("✅ Conexión actualizada: {}", platform);
        } else {
            connection = SocialConnection.builder()
                    .providerId(providerId)
                    .platform(platform)
                    .platformUserId(platformUserId)
                    .platformUserName(name)
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .profileImageUrl(picUrl)
                    .build();
            log.info("✅ Nueva conexión creada: {}", platform);
        }
        connectionRepository.save(connection);
    }
}
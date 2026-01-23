package com.quhealthy.social_service.service.publishing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quhealthy.social_service.model.ScheduledPost;
import com.quhealthy.social_service.model.SocialConnection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SocialPublishingService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper; // Para crear JSONs complejos

    /**
     * Orquestador que decide a qué red enviar el post.
     */
    public String publish(ScheduledPost post) {
        SocialConnection conn = post.getSocialConnection();
        String imageUrl = post.getMediaUrls().isEmpty() ? null : post.getMediaUrls().get(0);
        
        log.info("🚀 Publicando Post ID {} en {}", post.getId(), conn.getPlatform());

        try {
            return switch (conn.getPlatform()) {
                case FACEBOOK -> publishToFacebook(conn, post.getContent(), imageUrl);
                case INSTAGRAM -> publishToInstagram(conn, post.getContent(), imageUrl);
                case LINKEDIN -> publishToLinkedIn(conn, post.getContent(), imageUrl);
                default -> throw new IllegalArgumentException("Plataforma no soportada para publicación automática: " + conn.getPlatform());
            };
        } catch (Exception e) {
            log.error("❌ Fallo crítico publicando en {}: {}", conn.getPlatform(), e.getMessage());
            throw new RuntimeException(e);
        }
    }

    // =================================================================
    // 1. FACEBOOK PUBLISHING
    // =================================================================
    private String publishToFacebook(SocialConnection conn, String content, String imageUrl) {
        String url = String.format("https://graph.facebook.com/v19.0/%s/photos", conn.getPlatformUserId());
        
        Map<String, Object> body = new HashMap<>();
        body.put("url", imageUrl);
        body.put("caption", content);
        body.put("access_token", conn.getAccessToken());

        JsonNode response = webClient.post().uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        if (response != null && response.has("post_id")) {
            return response.get("post_id").asText();
        }
        throw new RuntimeException("Facebook no devolvió post_id");
    }

    // =================================================================
    // 2. INSTAGRAM PUBLISHING (Container Flow)
    // =================================================================
    private String publishToInstagram(SocialConnection conn, String content, String imageUrl) throws InterruptedException {
        // A. Obtener ID de cuenta de Instagram Business
        // (Nota: Asumimos que platformUserId ya es el IG Business ID, si no, habría que buscarlo)
        String igUserId = conn.getPlatformUserId();

        // B. Crear Contenedor
        String containerUrl = String.format("https://graph.facebook.com/v19.0/%s/media", igUserId);
        Map<String, Object> containerBody = new HashMap<>();
        containerBody.put("image_url", imageUrl);
        containerBody.put("caption", content);
        containerBody.put("access_token", conn.getAccessToken());

        JsonNode containerRes = webClient.post().uri(containerUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(containerBody)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        String creationId = containerRes.get("id").asText();
        log.info("📸 IG Container creado: {}", creationId);

        // C. Esperar procesamiento (Polling)
        waitForInstagramProcessing(creationId, conn.getAccessToken());

        // D. Publicar Contenedor
        String publishUrl = String.format("https://graph.facebook.com/v19.0/%s/media_publish", igUserId);
        Map<String, Object> publishBody = new HashMap<>();
        publishBody.put("creation_id", creationId);
        publishBody.put("access_token", conn.getAccessToken());

        JsonNode publishRes = webClient.post().uri(publishUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(publishBody)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        return publishRes.get("id").asText();
    }

    private void waitForInstagramProcessing(String containerId, String token) throws InterruptedException {
        int retries = 0;
        String status = "";
        
        while (!"FINISHED".equals(status) && retries < 10) {
            Thread.sleep(3000); // Esperar 3 seg
            
            String url = String.format("https://graph.facebook.com/v19.0/%s?fields=status_code&access_token=%s", containerId, token);
            JsonNode res = webClient.get().uri(url).retrieve().bodyToMono(JsonNode.class).block();
            
            status = res.get("status_code").asText();
            if ("ERROR".equals(status)) throw new RuntimeException("IG reportó error procesando imagen.");
            retries++;
        }
    }

    // =================================================================
    // 3. LINKEDIN PUBLISHING (3-Step Flow)
    // =================================================================
    private String publishToLinkedIn(SocialConnection conn, String content, String imageUrl) {
        String authorUrn = "urn:li:person:" + conn.getPlatformUserId();

        // Paso 1: Registrar Upload
        String registerUrl = "https://api.linkedin.com/v2/assets?action=registerUpload";
        String registerJson = """
            {
                "registerUploadRequest": {
                    "recipes": ["urn:li:digitalmediaRecipe:feedshare-image"],
                    "owner": "%s",
                    "serviceRelationships": [{"relationshipType": "OWNER", "identifier": "urn:li:userGeneratedContent"}]
                }
            }
            """.formatted(authorUrn);

        JsonNode regRes = webClient.post().uri(registerUrl)
                .header("Authorization", "Bearer " + conn.getAccessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(registerJson)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        String uploadUrl = regRes.get("value").get("uploadMechanism")
                .get("com.linkedin.digitalmedia.uploading.MediaUploadHttpRequest").get("uploadUrl").asText();
        String assetUrn = regRes.get("value").get("asset").asText();

        // Paso 2: Subir Imagen (Binario)
        // Primero descargamos la imagen de nuestra nube a bytes
        byte[] imageBytes = webClient.get().uri(imageUrl).retrieve().bodyToMono(byte[].class).block();
        
        webClient.put().uri(uploadUrl)
                .header("Authorization", "Bearer " + conn.getAccessToken())
                .contentType(MediaType.APPLICATION_OCTET_STREAM) // LinkedIn exige octet-stream
                .bodyValue(imageBytes)
                .retrieve()
                .toBodilessEntity()
                .block();

        // Paso 3: Crear Post UGC
        String postUrl = "https://api.linkedin.com/v2/ugcPosts";
        // Construcción manual del JSON complejo de LinkedIn
        Map<String, Object> postBody = Map.of(
            "author", authorUrn,
            "lifecycleState", "PUBLISHED",
            "specificContent", Map.of(
                "com.linkedin.ugc.ShareContent", Map.of(
                    "shareCommentary", Map.of("text", content),
                    "shareMediaCategory", "IMAGE",
                    "media", java.util.List.of(Map.of(
                        "status", "READY",
                        "media", assetUrn
                    ))
                )
            ),
            "visibility", Map.of("com.linkedin.ugc.MemberNetworkVisibility", "PUBLIC")
        );

        JsonNode postRes = webClient.post().uri(postUrl)
                .header("Authorization", "Bearer " + conn.getAccessToken())
                .header("X-Restli-Protocol-Version", "2.0.0") // Obligatorio para LinkedIn
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(postBody)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        return postRes.get("id").asText();
    }
}
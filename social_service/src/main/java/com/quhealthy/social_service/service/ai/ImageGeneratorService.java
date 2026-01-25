package com.quhealthy.social_service.service.ai;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.ImageConfig;
import com.google.genai.types.Part;
import com.quhealthy.social_service.dto.ai.AiImageRequest;
import com.quhealthy.social_service.dto.ai.AiImageResponse;
import com.quhealthy.social_service.dto.ai.AspectRatio;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImageGeneratorService {

    private final CloudStorageService cloudStorageService;

    @Value("${spring.cloud.gcp.project-id}")
    private String projectId;

    @Value("${spring.cloud.gcp.location:us-central1}")
    private String location;

    private static final String MODEL_NAME = "gemini-3-pro-image-preview";

    public AiImageResponse generateImage(AiImageRequest request) throws Exception {
        String sessionId = (request.getSessionId() != null) ? request.getSessionId() : UUID.randomUUID().toString();
        
        // Validación de seguridad (igual que antes)
        String actualProjectId = (projectId != null && !projectId.isEmpty()) 
                ? projectId 
                : System.getenv("GOOGLE_CLOUD_PROJECT");
        
        if (actualProjectId == null || actualProjectId.isEmpty()) {
            throw new RuntimeException("❌ ERROR FATAL: El Project ID es NULL.");
        }

        log.info("🎨 Iniciando generación con {} | Sesión: {}", MODEL_NAME, sessionId);

        String enhancedPrompt = enhancePrompt(request.getPrompt());
        String aspectRatioStr = mapAspectRatio(request.getAspectRatio());

        GenerateContentConfig config = GenerateContentConfig.builder()
                .responseModalities("IMAGE")
                .imageConfig(ImageConfig.builder()
                        .aspectRatio(aspectRatioStr)
                        .imageSize("2K")
                        .build())
                .build();

        // 👇 LA CORRECCIÓN MÁGICA ESTÁ AQUÍ
        try (Client client = Client.builder()
                .project(actualProjectId)
                .location(location)
                .vertexAI(true) // 👈 ¡ESTO FALTABA! Forzamos el modo Vertex AI
                .build()) {

            GenerateContentResponse response = client.models.generateContent(
                    MODEL_NAME,
                    enhancedPrompt,
                    config
            );

            for (Part part : response.parts()) {
                if (part.inlineData().isPresent()) {
                    var blob = part.inlineData().get();
                    if (blob.data().isPresent()) {
                        byte[] imageBytes = blob.data().get();
                        String mimeType = blob.mimeType().orElse("image/png");

                        String fileName = "ai-gen/" + sessionId + "/" + UUID.randomUUID() + "." + getExtension(mimeType);
                        String publicUrl = cloudStorageService.uploadFile(imageBytes, mimeType, fileName);

                        log.info("✅ Imagen generada: {}", publicUrl);

                        return AiImageResponse.builder()
                                .sessionId(sessionId)
                                .imageUrl(publicUrl)
                                .build();
                    }
                }
            }
            throw new RuntimeException("Gemini no devolvió datos de imagen válidos.");
        }
    }

    // --- Métodos Auxiliares ---

    private String enhancePrompt(String originalPrompt) {
        return originalPrompt + ", professional photograph, highly detailed, cinematic lighting, 4k resolution";
    }

    private String mapAspectRatio(AspectRatio ratio) {
        if (ratio == null) return "1:1";
        return switch (ratio) {
            case SQUARE -> "1:1";
            case PORTRAIT -> "9:16";
            case LANDSCAPE -> "16:9";
        };
    }

    private String getExtension(String mimeType) {
        if (mimeType == null) return "png";
        return switch (mimeType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> "png";
        };
    }
}
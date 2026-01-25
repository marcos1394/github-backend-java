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

    // Modelo Gemini 3 Pro para imágenes
    private static final String MODEL_NAME = "gemini-3-pro-image-preview";

    public AiImageResponse generateImage(AiImageRequest request) throws Exception {
        String sessionId = (request.getSessionId() != null) ? request.getSessionId() : UUID.randomUUID().toString();
        log.info("🎨 Iniciando generación de IMAGEN con {} | Sesión: {}", MODEL_NAME, sessionId);

        String enhancedPrompt = enhancePrompt(request.getPrompt());
        String aspectRatioStr = mapAspectRatio(request.getAspectRatio());

        // 1. Configuración de la petición
        GenerateContentConfig config = GenerateContentConfig.builder()
                .responseModalities("IMAGE")
                .imageConfig(ImageConfig.builder()
                        .aspectRatio(aspectRatioStr)
                        .imageSize("2K")
                        .build())
                .build();

        // 2. Instanciación del Cliente (CORREGIDO: Usando Builder)
        // Esto evita el error de "no suitable constructor"
        try (Client client = Client.builder()
                .project(projectId)
                .location(location)
                .build()) {

            // 3. Llamada al Modelo
            GenerateContentResponse response = client.models.generateContent(
                    MODEL_NAME,
                    enhancedPrompt,
                    config
            );

            // 4. Procesamiento de la Respuesta (CORREGIDO: Manejo de Optionals)
            for (Part part : response.parts()) {
                if (part.inlineData().isPresent()) {
                    var blob = part.inlineData().get();
                    if (blob.data().isPresent()) {
                        byte[] imageBytes = blob.data().get();
                        
                        // FIX: El SDK devuelve Optional<String>, usamos orElse para obtener el String
                        String mimeType = blob.mimeType().orElse("image/png");

                        // Subir a Cloud Storage
                        String fileName = "ai-gen/" + sessionId + "/" + UUID.randomUUID() + "." + getExtension(mimeType);
                        String publicUrl = cloudStorageService.uploadFile(imageBytes, mimeType, fileName);

                        log.info("✅ Imagen generada y subida: {}", publicUrl);

                        return AiImageResponse.builder()
                                .sessionId(sessionId)
                                .imageUrl(publicUrl)
                                .build();
                    }
                }
            }
            throw new RuntimeException("Gemini no devolvió datos de imagen válidos.");
        } catch (Exception e) {
            log.error("❌ Error generando imagen: {}", e.getMessage(), e);
            throw e; // Re-lanzamos para que el Controller lo capture
        }
    }

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
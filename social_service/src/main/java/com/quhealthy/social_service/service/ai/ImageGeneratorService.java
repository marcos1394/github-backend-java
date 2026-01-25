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

    // 🚀 MODELO: Gemini 3 Pro Image Preview (Calidad Profesional)
    private static final String MODEL_NAME = "gemini-3-pro-image-preview";

    public AiImageResponse generateImage(AiImageRequest request) throws Exception {
        String sessionId = (request.getSessionId() != null) ? request.getSessionId() : UUID.randomUUID().toString();
        log.info("🎨 Iniciando generación de IMAGEN con {} | Sesión: {}", MODEL_NAME, sessionId);

        // 1. Ingeniería de Prompt (Mejoramos la descripción para calidad visual)
        String enhancedPrompt = enhancePrompt(request.getPrompt());

        // 2. Configuración (Aspect Ratio y Resolución)
        String aspectRatioStr = mapAspectRatio(request.getAspectRatio());
        GenerateContentConfig config = GenerateContentConfig.builder()
                .responseModalities("IMAGE") // Solo queremos la imagen
                .imageConfig(ImageConfig.builder()
                        .aspectRatio(aspectRatioStr)
                        .imageSize("2K") // Usamos 2K para calidad profesional (opcional: "4K")
                        .build())
                .build();

        // 3. Llamada al Modelo (Usando el Cliente Nativo, como en la doc)
        // El cliente usa automáticamente las credenciales de Cloud Run (OAuth2 implícito)
        try (Client client = new Client(projectId, location)) {
            GenerateContentResponse response = client.models.generateContent(
                    MODEL_NAME,
                    enhancedPrompt,
                    config
            );

            // 4. Procesar la Respuesta
            for (Part part : response.parts()) {
                // Buscamos la parte que tiene datos binarios (la imagen)
                if (part.inlineData().isPresent()) {
                    var blob = part.inlineData().get();
                    if (blob.data().isPresent()) {
                        byte[] imageBytes = blob.data().get();
                        String mimeType = blob.mimeType(); // ej: "image/png"

                        // 5. Subir a Cloud Storage
                        String fileName = "ai-gen/" + sessionId + "/" + UUID.randomUUID() + "." + getExtension(mimeType);
                        String publicUrl = cloudStorageService.uploadFile(imageBytes, mimeType, fileName);

                        log.info("✅ Imagen Gemini 3 Pro generada y subida: {}", publicUrl);

                        return AiImageResponse.builder()
                                .sessionId(sessionId)
                                .imageUrl(publicUrl)
                                .build();
                    }
                }
            }
            throw new RuntimeException("Gemini 3 Pro no devolvió datos de imagen válidos.");
        } catch (Exception e) {
            log.error("❌ Error generando imagen con Gemini 3 Pro: {}", e.getMessage(), e);
            throw e; // Re-lanzamos para que el Controller lo capture
        }
    }

    // --- Métodos Auxiliares ---

    private String enhancePrompt(String originalPrompt) {
        // Agregamos modificadores de estilo para asegurar calidad profesional
        return originalPrompt + ", professional photograph, highly detailed, cinematic lighting, 4k resolution, hyper-realistic";
    }

    private String mapAspectRatio(AspectRatio ratio) {
        if (ratio == null) return "1:1";
        return switch (ratio) {
            case SQUARE -> "1:1";
            case PORTRAIT -> "9:16"; // Gemini usa formato "ancho:alto"
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
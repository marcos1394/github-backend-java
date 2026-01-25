package com.quhealthy.social_service.service.ai;

import com.google.genai.Client;
import com.google.genai.types.GenerateVideosConfig;
import com.google.genai.types.GenerateVideosOperation;
import com.google.genai.types.Video;
import com.quhealthy.social_service.dto.ai.AiVideoRequest;
import com.quhealthy.social_service.dto.ai.AiVideoResponse;
import com.quhealthy.social_service.dto.ai.AspectRatio;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoGeneratorService {

    private final CloudStorageService cloudStorageService;

    @Value("${spring.cloud.gcp.project-id}")
    private String projectId;

    @Value("${spring.cloud.gcp.location:us-central1}")
    private String location;

    // 🚀 MODELO: Veo 3.1 Preview
    private static final String MODEL_NAME = "veo-3.1-generate-preview";

    public AiVideoResponse generateVideo(AiVideoRequest request) throws Exception {
        String sessionId = (request.getSessionId() != null) ? request.getSessionId() : UUID.randomUUID().toString();
        
        String actualProjectId = (projectId != null && !projectId.isEmpty()) 
                ? projectId 
                : System.getenv("GOOGLE_CLOUD_PROJECT");

        if (actualProjectId == null) {
            throw new RuntimeException("Project ID no configurado.");
        }

        log.info("🎬 Iniciando Veo 3.1 (SDK Vertex AI) | Sesión: {}", sessionId);

        String targetResolution = (request.getResolution() != null) ? request.getResolution() : "720p";
        String targetAspectRatio = mapAspectRatio(request.getAspectRatio());

        GenerateVideosConfig config = GenerateVideosConfig.builder()
                .aspectRatio(targetAspectRatio)
                .build();

        // 🛠️ CORRECCIÓN 1: Usamos .vertexAI(true) en lugar de .backend(...)
        try (Client client = Client.builder()
                .project(actualProjectId)
                .location(location)
                .vertexAI(true) 
                .build()) {

            log.info("📡 Enviando prompt a Veo: '{}'", request.getPrompt());
            
            // 🛠️ CORRECCIÓN 2: Agregamos 'null' como 3er argumento (Imagen base)
            // Firma: (String model, String prompt, Image image, GenerateVideosConfig config)
            GenerateVideosOperation operation = client.models.generateVideos(
                    MODEL_NAME,
                    request.getPrompt(),
                    null, 
                    config
            );

            log.info("⏳ Operación iniciada. Esperando renderizado (Polling)...");

            // POLLING LOOP
            while (!operation.done().orElse(false)) {
                Thread.sleep(10000); // Espera 10 segundos
                
                // 🛠️ CORRECCIÓN 3: Pasamos el objeto 'operation' completo, no operation.name()
                operation = client.operations.getVideosOperation(operation, null);
            }

            log.info("✨ Generación de Veo finalizada. Procesando resultado...");

            if (operation.response().isPresent() && 
                !operation.response().get().generatedVideos().get().isEmpty()) {
                
                Video video = operation.response().get().generatedVideos().get().get(0).video().get();

                // 🛠️ CORRECCIÓN 4: El SDK devuelve byte[] directo, no ByteBuffer
                if (video.videoBytes().isPresent()) {
                    byte[] videoBytes = video.videoBytes().get();

                    // Subir a Cloud Storage
                    String fileName = "ai-gen/" + sessionId + "/" + UUID.randomUUID() + ".mp4";
                    String publicUrl = cloudStorageService.uploadFile(videoBytes, "video/mp4", fileName);

                    log.info("✅ Video Veo generado y subido: {}", publicUrl);

                    return AiVideoResponse.builder()
                            .sessionId(sessionId)
                            .videoUrl(publicUrl)
                            .resolution(targetResolution)
                            .duration("8s")
                            .build();
                }
            }
            
            throw new RuntimeException("Veo terminó pero no devolvió datos de video.");

        } catch (Exception e) {
            log.error("❌ Error CRÍTICO generando video con Veo: {}", e.getMessage(), e);
            throw e;
        }
    }

    // --- Métodos Auxiliares ---

    private String mapAspectRatio(AspectRatio ratio) {
        if (ratio == null) return "16:9";
        return switch (ratio) {
            case PORTRAIT -> "9:16";
            case SQUARE -> "1:1";
            default -> "16:9";
        };
    }
}
package com.quhealthy.social_service.service.ai;

import com.google.genai.Client;
import com.google.genai.types.GenerateVideosConfig;
import com.google.genai.types.GenerateVideosOperation;
import com.google.genai.types.GetOperationConfig;
import com.google.genai.types.Image;
import com.google.genai.types.Video;
import com.quhealthy.social_service.dto.ai.AiVideoRequest;
import com.quhealthy.social_service.dto.ai.AiVideoResponse;
import com.quhealthy.social_service.dto.ai.VideoAspectRatio;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URL;
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

    public AiVideoResponse generateVideo(AiVideoRequest request) throws Exception {
        log.info("🎬 Iniciando generación de video Veo 3.1...");

        try (Client client = Client.builder()
                .project(projectId)
                .location(location)
                .build()) {

            GenerateVideosConfig.Builder configBuilder = GenerateVideosConfig.builder();
            
            if (request.getAspectRatio() != null) {
                configBuilder.aspectRatio(request.getAspectRatio().getValue());
            } else {
                configBuilder.aspectRatio(VideoAspectRatio.LANDSCAPE_16_9.getValue());
            }

            if (request.isHdResolution()) {
                configBuilder.resolution("1080p");
            } else {
                configBuilder.resolution("720p");
            }

            Image imageInput = null;
            if (request.getImageUrl() != null && !request.getImageUrl().isEmpty()) {
                try (InputStream in = new URL(request.getImageUrl()).openStream()) {
                    byte[] rawBytes = in.readAllBytes();
                    
                    imageInput = Image.builder()
                            .imageBytes(rawBytes)
                            .mimeType("image/jpeg")
                            .build();
                }
            }

            String modelName = "veo-3.1-generate-preview";
            GenerateVideosOperation operation;

            if (imageInput != null) {
                log.info("🖼️ Modo Image-to-Video activado");
                operation = client.models.generateVideos(modelName, request.getPrompt(), imageInput, configBuilder.build());
            } else {
                log.info("📝 Modo Text-to-Video activado");
                operation = client.models.generateVideos(modelName, request.getPrompt(), null, configBuilder.build());
            }

            log.info("⏳ Esperando renderizado del video...");

            // Bucle de Polling
            while (!operation.done().orElse(false)) {
                Thread.sleep(10000); // 10 segundos
                
                // Configuración vacía (necesaria para la sobrecarga)
                GetOperationConfig emptyConfig = GetOperationConfig.builder().build();
                
                // --- CORRECCIÓN FINAL ---
                // En lugar de 'getVideosOperation' (que falla), usamos el método genérico 'get'.
                // Como 'operation' es de tipo GenerateVideosOperation, Java sabe qué devolver.
                operation = client.operations.get(operation, emptyConfig);
            }

            if (operation.response().isEmpty() || operation.response().get().generatedVideos().isEmpty()) {
                 throw new RuntimeException("La generación de video terminó pero no hay resultados.");
            }

            Video generatedVideo = operation.response().get().generatedVideos().get().get(0).video().get();

            byte[] videoBytes;
            if (generatedVideo.videoBytes().isPresent()) {
                videoBytes = generatedVideo.videoBytes().get();
            } else {
                 throw new RuntimeException("No se encontraron bytes de video.");
            }

            String fileName = "veo-" + UUID.randomUUID() + ".mp4";
            String publicUrl = cloudStorageService.uploadFile(videoBytes, "video/mp4", "ai-generated-videos");

            log.info("✅ Video generado y subido: {}", publicUrl);

            return AiVideoResponse.builder()
                    .videoUrl(publicUrl)
                    .resolution(request.isHdResolution() ? "1080p" : "720p")
                    .duration("8s")
                    .build();
        }
    }
}
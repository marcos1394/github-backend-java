package com.quhealthy.social_service.service.ai;

import com.google.genai.Client;
import com.google.genai.types.GenerateVideosConfig;
import com.google.genai.types.GenerateVideosOperation;
import com.google.genai.types.Image;
import com.google.genai.types.Video;
import com.quhealthy.social_service.dto.ai.AiVideoRequest;
import com.quhealthy.social_service.dto.ai.AiVideoResponse;
import com.quhealthy.social_service.dto.ai.VideoAspectRatio;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

        // 1. Inicializar Cliente (Usando Vertex AI backend)
        // El SDK detecta las credenciales de Cloud Run automáticamente
        try (Client client = new Client(Client.Builder.newBuilder()
                .setProjectId(projectId)
                .setLocation(location)
                .setVertexAi(true) // Importante: Usar modo Vertex AI, no API Key
                .build())) {

            // 2. Configurar Parámetros
            GenerateVideosConfig.Builder configBuilder = GenerateVideosConfig.newBuilder()
                    .setAspectRatio(request.getAspectRatio() != null 
                            ? request.getAspectRatio().getValue() 
                            : VideoAspectRatio.LANDSCAPE_16_9.getValue());

            if (request.isHdResolution()) {
                configBuilder.setResolution("1080p");
            } else {
                configBuilder.setResolution("720p");
            }

            // 3. Preparar Imagen Base (si existe)
            Image imageInput = null;
            if (request.getImageUrl() != null && !request.getImageUrl().isEmpty()) {
                // Descargar la imagen de la URL para pasarla como bytes/objeto al modelo
                // Nota: En producción, optimizaríamos esto pasando referencias de Cloud Storage si el SDK lo permite.
                // Aquí usamos la utilidad 'fromUri' si el SDK la soporta, o descargamos.
                // Asumimos que la URL es pública.
                 imageInput = Image.fromUri(request.getImageUrl());
            }

            // 4. Lanzar Operación (Async)
            String modelName = "veo-3.1-generate-preview";
            GenerateVideosOperation operation;
            
            if (imageInput != null) {
                log.info("🖼️ Modo Image-to-Video activado");
                operation = client.models.generateVideos(modelName, request.getPrompt(), imageInput, configBuilder.build());
            } else {
                log.info("📝 Modo Text-to-Video activado");
                operation = client.models.generateVideos(modelName, request.getPrompt(), null, configBuilder.build());
            }

            // 5. Polling (Esperar a que termine)
            log.info("⏳ Esperando renderizado del video...");
            while (!operation.done().orElse(false)) {
                Thread.sleep(5000); // Esperar 5 segundos
                operation = client.operations.getVideosOperation(operation, null);
            }

            // 6. Obtener Resultado
            if (operation.response().isEmpty() || operation.response().get().generatedVideos().isEmpty()) {
                throw new RuntimeException("La generación de video falló o no devolvió resultados.");
            }

            Video generatedVideo = operation.response().get().generatedVideos().get().get(0).video().get();

            // 7. Subir a Cloud Storage
            // El video viene como bytes en la respuesta (si es pequeño) o una referencia.
            // La documentación dice 'video.videoBytes()'.
            byte[] videoBytes;
            if (generatedVideo.videoBytes().isPresent()) {
                videoBytes = generatedVideo.videoBytes().get().array();
            } else if (generatedVideo.uri().isPresent()) {
                // Si devuelve una URI (ej: de GCS temporal), tendríamos que descargarla.
                // Por simplicidad del ejemplo y doc, asumimos bytes presentes o descarga directa.
                // En un caso real, implementaríamos descarga desde URI.
                throw new RuntimeException("El video se generó pero solo tenemos URI (Pendiente de implementar descarga): " + generatedVideo.uri().get());
            } else {
                 throw new RuntimeException("No se encontraron bytes de video en la respuesta.");
            }

            String fileName = "veo-" + UUID.randomUUID() + ".mp4";
            String publicUrl = cloudStorageService.uploadFile(videoBytes, "video/mp4", "ai-generated-videos");

            log.info("✅ Video generado y subido: {}", publicUrl);

            return AiVideoResponse.builder()
                    .videoUrl(publicUrl)
                    .resolution(request.isHdResolution() ? "1080p" : "720p")
                    .duration("8s") // Veo 3.1 genera clips cortos por defecto
                    .build();
        }
    }
}
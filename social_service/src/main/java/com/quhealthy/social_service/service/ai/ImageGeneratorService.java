package com.quhealthy.social_service.service.ai;

import com.google.cloud.vertexai.api.Candidate;
import com.google.cloud.vertexai.api.Content;
import com.google.cloud.vertexai.api.GenerateContentResponse;
import com.google.cloud.vertexai.api.Part;
import com.google.cloud.vertexai.generativeai.ChatSession;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import com.google.protobuf.ByteString;
import com.quhealthy.social_service.dto.ai.AiImageRequest;
import com.quhealthy.social_service.dto.ai.AiImageResponse;
import com.quhealthy.social_service.dto.ai.AspectRatio;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImageGeneratorService {

    // Inyectamos el modelo "Pro Image" desde AiConfig
    @Qualifier("imageModel")
    private final GenerativeModel imageModel;

    private final RedisTemplate<String, Object> redisTemplate;
    private final CloudStorageService cloudStorageService;

    private static final String REDIS_PREFIX = "chat:image-history:";
    private static final Duration SESSION_TTL = Duration.ofMinutes(45); // Un poco más de tiempo para edición

    public AiImageResponse generateImage(AiImageRequest request) throws IOException {
        String sessionId = request.getSessionId();
        List<Content> history = new ArrayList<>();

        // 1. Gestión de Sesión (Redis)
        if (sessionId != null && !sessionId.isEmpty()) {
            Object cachedHistory = redisTemplate.opsForValue().get(REDIS_PREFIX + sessionId);
            if (cachedHistory != null) {
                // Warning: Unchecked cast, confiamos en el serializer JSON
                history = (List<Content>) cachedHistory;
                log.info("🎨 Contexto visual recuperado para sesión: {}", sessionId);
            }
        } else {
            sessionId = UUID.randomUUID().toString();
            log.info("✨ Nueva sesión de generación de imagen: {}", sessionId);
        }

        // 2. Preparar el Prompt (Incluyendo Aspect Ratio como instrucción de texto)
        // Gemini 3 entiende instrucciones técnicas dentro del prompt.
        String finalPrompt = request.getPrompt();
        if (request.getAspectRatio() != null) {
            finalPrompt += " Aspect Ratio: " + mapAspectRatio(request.getAspectRatio());
        }

        // 3. Iniciar Chat y Enviar
        ChatSession chatSession = new ChatSession(imageModel, history);
        GenerateContentResponse response = chatSession.sendMessage(finalPrompt);

        // 4. Extraer la imagen de la respuesta
        ImageData extractedImage = extractImageFromResponse(response);

        // 5. Subir a Cloud Storage
        String publicUrl = cloudStorageService.uploadFile(
                extractedImage.bytes.toByteArray(),
                extractedImage.mimeType,
                "ai-generated-images" // Carpeta en el bucket
        );

        // 6. Actualizar Historial en Redis
        // Es crucial guardar el historial después de la respuesta para mantener la "conversación visual"
        redisTemplate.opsForValue().set(REDIS_PREFIX + sessionId, chatSession.getHistory(), SESSION_TTL);

        return AiImageResponse.builder()
                .sessionId(sessionId)
                .imageUrl(publicUrl)
                .build();
    }

    // Helper para convertir el Enum a texto que Gemini entienda
    private String mapAspectRatio(AspectRatio ratio) {
        return switch (ratio) {
            case SQUARE -> "1:1";
            case PORTRAIT -> "9:16";
            case LANDSCAPE -> "16:9";
        };
    }

    // Helper crítico: Busca la parte binaria (imagen) en la respuesta compleja de Gemini
    private ImageData extractImageFromResponse(GenerateContentResponse response) {
        for (Candidate candidate : response.getCandidatesList()) {
            for (Part part : candidate.getContent().getPartsList()) {
                // Buscamos una parte que tenga datos en línea y sea de tipo imagen
                if (part.hasInlineData() && part.getInlineData().getMimeType().startsWith("image/")) {
                    return new ImageData(
                            part.getInlineData().getData(),
                            part.getInlineData().getMimeType()
                    );
                }
            }
        }
        throw new RuntimeException("Gemini no devolvió ninguna imagen válida en la respuesta.");
    }

    // Clase interna simple para transportar los datos extraídos
    private record ImageData(ByteString bytes, String mimeType) {}
}
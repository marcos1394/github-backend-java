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

    @Qualifier("imageModel")
    private final GenerativeModel imageModel;

    private final RedisTemplate<String, Object> redisTemplate;
    private final CloudStorageService cloudStorageService;

    private static final String REDIS_PREFIX = "chat:image-history:";
    private static final Duration SESSION_TTL = Duration.ofMinutes(45);

    public AiImageResponse generateImage(AiImageRequest request) throws IOException {
        String sessionId = request.getSessionId();
        List<Content> history = new ArrayList<>();

        if (sessionId != null && !sessionId.isEmpty()) {
            Object cachedHistory = redisTemplate.opsForValue().get(REDIS_PREFIX + sessionId);
            if (cachedHistory != null) {
                history = (List<Content>) cachedHistory;
                log.info("🎨 Contexto visual recuperado para sesión: {}", sessionId);
            }
        } else {
            sessionId = UUID.randomUUID().toString();
        }

        String finalPrompt = request.getPrompt();
        if (request.getAspectRatio() != null) {
            finalPrompt += " Aspect Ratio: " + mapAspectRatio(request.getAspectRatio());
        }

        // --- CORRECCIÓN AQUÍ ---
        ChatSession chatSession = new ChatSession(imageModel);
        if (!history.isEmpty()) {
            chatSession.setHistory(history);
        }
        // -----------------------

        GenerateContentResponse response = chatSession.sendMessage(finalPrompt);
        ImageData extractedImage = extractImageFromResponse(response);

        String publicUrl = cloudStorageService.uploadFile(
                extractedImage.bytes.toByteArray(),
                extractedImage.mimeType,
                "ai-generated-images"
        );

        redisTemplate.opsForValue().set(REDIS_PREFIX + sessionId, chatSession.getHistory(), SESSION_TTL);

        return AiImageResponse.builder()
                .sessionId(sessionId)
                .imageUrl(publicUrl)
                .build();
    }

    private String mapAspectRatio(AspectRatio ratio) {
        return switch (ratio) {
            case SQUARE -> "1:1";
            case PORTRAIT -> "9:16";
            case LANDSCAPE -> "16:9";
        };
    }

    private ImageData extractImageFromResponse(GenerateContentResponse response) {
        for (Candidate candidate : response.getCandidatesList()) {
            for (Part part : candidate.getContent().getPartsList()) {
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

    private record ImageData(ByteString bytes, String mimeType) {}
}
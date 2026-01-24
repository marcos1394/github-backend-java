package com.quhealthy.social_service.service.ai;

import com.google.cloud.vertexai.api.Content;
import com.google.cloud.vertexai.api.GenerateContentResponse;
import com.google.cloud.vertexai.generativeai.ChatSession;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import com.google.cloud.vertexai.generativeai.ResponseHandler;
import com.quhealthy.social_service.dto.ai.AiTextRequest;
import com.quhealthy.social_service.dto.ai.AiTextResponse;
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
public class ContentGeneratorService {

    @Qualifier("textModel")
    private final GenerativeModel textModel;

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String REDIS_PREFIX = "chat:history:";
    private static final Duration SESSION_TTL = Duration.ofMinutes(30);

    public AiTextResponse generatePostText(AiTextRequest request) throws IOException {
        String sessionId = request.getSessionId();
        List<Content> history = new ArrayList<>();

        if (sessionId != null && !sessionId.isEmpty()) {
            Object cachedHistory = redisTemplate.opsForValue().get(REDIS_PREFIX + sessionId);
            if (cachedHistory != null) {
                history = (List<Content>) cachedHistory;
                log.info("🧠 Contexto recuperado para sesión: {}", sessionId);
            }
        } else {
            sessionId = UUID.randomUUID().toString();
            log.info("✨ Nueva sesión de IA iniciada: {}", sessionId);
        }

        // --- CORRECCIÓN AQUÍ ---
        // El constructor ChatSession(model, history) ya no existe.
        // Creamos la sesión con el modelo y setteamos el historial manualmente.
        ChatSession chatSession = new ChatSession(textModel);
        if (!history.isEmpty()) {
            chatSession.setHistory(history);
        }
        // -----------------------

        String finalPrompt = request.getPrompt();
        if (request.getTone() != null && !request.getTone().isEmpty()) {
            finalPrompt += " (Tono: " + request.getTone() + ")";
        }

        GenerateContentResponse response = chatSession.sendMessage(finalPrompt);
        String responseText = ResponseHandler.getText(response);

        // Guardamos el historial actualizado
        redisTemplate.opsForValue().set(REDIS_PREFIX + sessionId, chatSession.getHistory(), SESSION_TTL);

        return AiTextResponse.builder()
                .sessionId(sessionId)
                .generatedText(responseText)
                .build();
    }
}
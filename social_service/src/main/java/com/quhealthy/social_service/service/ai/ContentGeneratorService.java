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

    // Inyectamos el modelo "Flash" que configuramos en AiConfig
    @Qualifier("textModel")
    private final GenerativeModel textModel;

    // Redis para guardar el historial (Contexto + Firmas de Pensamiento)
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String REDIS_PREFIX = "chat:history:";
    private static final Duration SESSION_TTL = Duration.ofMinutes(30); // El contexto vive 30 mins

    /**
     * Genera texto manteniendo el contexto de la conversación.
     */
    public AiTextResponse generatePostText(AiTextRequest request) throws IOException {
        String sessionId = request.getSessionId();
        List<Content> history = new ArrayList<>();

        // 1. Si hay sesión, intentamos recuperar el historial de Redis
        if (sessionId != null && !sessionId.isEmpty()) {
            Object cachedHistory = redisTemplate.opsForValue().get(REDIS_PREFIX + sessionId);
            if (cachedHistory != null) {
                // Warning: Unchecked cast, pero seguro si Redis serializer es JSON
                history = (List<Content>) cachedHistory;
                log.info("🧠 Contexto recuperado para sesión: {}", sessionId);
            }
        } else {
            // Si no hay sesión, creamos una nueva
            sessionId = UUID.randomUUID().toString();
            log.info("✨ Nueva sesión de IA iniciada: {}", sessionId);
        }

        // 2. Iniciar Chat con Gemini (Rehidratando historial si existe)
        ChatSession chatSession = new ChatSession(textModel, history);

        // 3. Preparar el Prompt (Incluyendo el tono si se pide)
        String finalPrompt = request.getPrompt();
        if (request.getTone() != null && !request.getTone().isEmpty()) {
            finalPrompt += " (Tono: " + request.getTone() + ")";
        }

        // 4. Enviar mensaje a Vertex AI
        // Gemini 3 Flash procesará esto rápido
        GenerateContentResponse response = chatSession.sendMessage(finalPrompt);
        String responseText = ResponseHandler.getText(response);

        // 5. Guardar el nuevo historial en Redis
        // .getHistory() devuelve la lista completa acumulada (User + AI + Thoughts)
        List<Content> updatedHistory = chatSession.getHistory();
        redisTemplate.opsForValue().set(REDIS_PREFIX + sessionId, updatedHistory, SESSION_TTL);

        return AiTextResponse.builder()
                .sessionId(sessionId)
                .generatedText(responseText)
                .build();
    }
}
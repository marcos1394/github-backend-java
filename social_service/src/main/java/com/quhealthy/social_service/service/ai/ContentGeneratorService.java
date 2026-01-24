package com.quhealthy.social_service.service.ai;

import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.api.Content;
import com.google.cloud.vertexai.api.GenerateContentResponse;
import com.google.cloud.vertexai.generativeai.ChatSession;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import com.google.cloud.vertexai.generativeai.ResponseHandler;
import com.quhealthy.social_service.dto.ai.AiTextRequest;
import com.quhealthy.social_service.dto.ai.AiTextResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

    // Inyectamos Redis
    private final RedisTemplate<String, Object> redisTemplate;

    // Inyectamos variables de proyecto (necesarias para instanciar el modelo aquí mismo)
    @Value("${spring.cloud.gcp.project-id}")
    private String projectId;

    @Value("${spring.cloud.gcp.location:us-central1}")
    private String location;

    private static final String REDIS_PREFIX = "chat:history:";
    private static final Duration SESSION_TTL = Duration.ofMinutes(30);

    public AiTextResponse generatePostText(AiTextRequest request) throws Exception {
        String sessionId = request.getSessionId();
        
        // 1. Gestión de Sesión (Crear o Recuperar)
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = UUID.randomUUID().toString();
            log.info("✨ Nueva sesión de IA iniciada: {}", sessionId);
        } else {
            log.info("🔄 Continuando sesión: {}", sessionId);
        }

        // 2. Recuperar Historial de Redis
        List<Content> history = new ArrayList<>();
        try {
            Object cachedHistory = redisTemplate.opsForValue().get(REDIS_PREFIX + sessionId);
            if (cachedHistory != null) {
                // Nota: Asegúrate de que tu configuración de Redis serialice bien los objetos de Google
                history = (List<Content>) cachedHistory; 
                log.info("🧠 Memoria recuperada: {} mensajes previos", history.size());
            }
        } catch (Exception e) {
            log.error("⚠️ Error leyendo Redis (iniciando chat limpio): {}", e.getMessage());
            // Si falla Redis, no rompemos el flujo, solo empezamos sin memoria
        }

        // 3. Instanciar Vertex AI y el Modelo (Gemini 2.5 Flash)
        // Usamos try-with-resources para asegurar que VertexAI se cierre correctamente
        try (VertexAI vertexAI = new VertexAI(projectId, location)) {
            
            // 🚀 AQUÍ DEFINIMOS EL MODELO 2.5 FLASH
            GenerativeModel model = new GenerativeModel("gemini-2.5-flash", vertexAI);

            // 4. Iniciar Sesión de Chat
            ChatSession chatSession = new ChatSession(model);
            
            // Si hay historial previo, lo inyectamos
            if (!history.isEmpty()) {
                chatSession.setHistory(history);
            }

            // 5. Preparar el Prompt con Tono
            String finalPrompt = request.getPrompt();
            if (request.getTone() != null && !request.getTone().isEmpty()) {
                finalPrompt += "\n\n(Instrucción de Tono: Responde con un estilo " + request.getTone() + ")";
            }

            // 6. Enviar mensaje a Google
            GenerateContentResponse response = chatSession.sendMessage(finalPrompt);
            String responseText = ResponseHandler.getText(response);

            // 7. Guardar el nuevo historial en Redis (Async o Sync)
            try {
                // Obtenemos el historial actualizado (incluye la nueva respuesta)
                List<Content> updatedHistory = chatSession.getHistory();
                redisTemplate.opsForValue().set(REDIS_PREFIX + sessionId, updatedHistory, SESSION_TTL);
            } catch (Exception e) {
                log.error("⚠️ No se pudo guardar el historial en Redis: {}", e.getMessage());
            }

            return AiTextResponse.builder()
                    .sessionId(sessionId)
                    .generatedContent(responseText) // Ajustado al nombre de campo de tu DTO
                    .usedModel("gemini-2.5-flash")
                    .build();
        }
    }
}
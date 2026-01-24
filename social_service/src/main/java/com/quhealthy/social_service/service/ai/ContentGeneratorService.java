package com.quhealthy.social_service.service.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.quhealthy.social_service.dto.ai.AiTextRequest;
import com.quhealthy.social_service.dto.ai.AiTextResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContentGeneratorService {

    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    
    // Cliente HTTP optimizado para Java 21 (Reutilizable)
    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Value("${google.ai.api-key}")
    private String apiKey;

    // 🚀 CONFIGURACIÓN DEL MODELO "FRONTIER"
    // Usamos 'gemini-2.0-flash-exp' que es la versión pública actual de la nueva generación.
    // Si tu organización tiene acceso whitelist a 'gemini-3-flash-preview', cámbialo aquí.
    private static final String MODEL_NAME = "gemini-3-flash-preview"; 
    
    private static final String API_URL_TEMPLATE = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";
    private static final String REDIS_PREFIX = "chat:history:";
    private static final Duration SESSION_TTL = Duration.ofMinutes(30);

    public AiTextResponse generatePostText(AiTextRequest request) throws Exception {
        // 1. Gestión de Sesión
        String sessionId = (request.getSessionId() != null && !request.getSessionId().isEmpty()) 
                ? request.getSessionId() 
                : UUID.randomUUID().toString();

        log.info("✨ Iniciando generación Enterprise con {} | Sesión: {}", MODEL_NAME, sessionId);

        // 2. Construir Historial (Contexto)
        ArrayNode contentsArray = objectMapper.createArrayNode();
        List<ObjectNode> historyList = loadHistoryFromRedis(sessionId);

        // Agregamos el historial previo al payload
        for (ObjectNode msg : historyList) {
            contentsArray.add(msg);
        }

        // 3. Ingeniería de Prompt (Nuevo Mensaje)
        String engineeredPrompt = buildPrompt(request);
        
        // Crear nodo del mensaje actual (User)
        ObjectNode currentUserMessage = objectMapper.createObjectNode();
        currentUserMessage.put("role", "user");
        currentUserMessage.putArray("parts").addObject().put("text", engineeredPrompt);
        
        // Agregarlo a la lista de envío
        contentsArray.add(currentUserMessage);

        // 4. Construir Payload JSON Completo
        ObjectNode rootPayload = objectMapper.createObjectNode();
        rootPayload.set("contents", contentsArray);

        // Configuración de Generación (Temperatura, Tokens)
        ObjectNode generationConfig = rootPayload.putObject("generationConfig");
        generationConfig.put("temperature", 0.7);
        generationConfig.put("maxOutputTokens", 800);

        // 5. Ejecutar Petición HTTP (REST Nativo)
        String endpoint = String.format(API_URL_TEMPLATE, MODEL_NAME, apiKey);
        
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(rootPayload.toString(), StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

        // 6. Manejo de Errores y Respuesta
        if (response.statusCode() != 200) {
            log.error("❌ Error API Google AI ({}): {}", response.statusCode(), response.body());
            throw new RuntimeException("Fallo en la generación de IA: " + response.body());
        }

        // 7. Parsear Respuesta Exitosa
        String generatedText = extractTextFromResponse(response.body());

        // 8. Actualizar Memoria (Redis)
        // Agregamos lo que dijimos nosotros
        historyList.add(currentUserMessage);
        
        // Agregamos lo que respondió la IA
        ObjectNode modelResponseNode = objectMapper.createObjectNode();
        modelResponseNode.put("role", "model");
        modelResponseNode.putArray("parts").addObject().put("text", generatedText);
        historyList.add(modelResponseNode);

        saveHistoryToRedis(sessionId, historyList);

        return AiTextResponse.builder()
                .sessionId(sessionId)
                .generatedText(generatedText)
                .usedModel(MODEL_NAME)
                .build();
    }

    // --- Métodos Auxiliares ---

    private String buildPrompt(AiTextRequest request) {
        String basePrompt = request.getPrompt();
        String toneInstruction = (request.getTone() != null) 
                ? " Tono deseado: " + request.getTone() + "." 
                : "";
        
        return String.format("""
            Actúa como experto en Social Media para 'QuHealthy'.
            Tarea: %s
            %s
            Requisitos: Usa emojis, sé breve y usa 3 hashtags al final.
            """, basePrompt, toneInstruction);
    }

    private String extractTextFromResponse(String jsonBody) {
        try {
            JsonNode root = objectMapper.readTree(jsonBody);
            return root.path("candidates").get(0)
                    .path("content").path("parts").get(0)
                    .path("text").asText();
        } catch (Exception e) {
            log.error("⚠️ Error parseando JSON de Gemini: {}", jsonBody);
            throw new RuntimeException("La IA respondió pero no pude leer el texto.");
        }
    }

    // --- Persistencia en Redis (Manejo Manual de JSON) ---

    private List<ObjectNode> loadHistoryFromRedis(String sessionId) {
        try {
            Object cached = redisTemplate.opsForValue().get(REDIS_PREFIX + sessionId);
            if (cached != null) {
                // Deserializamos la lista de objetos JSON
                return objectMapper.convertValue(cached, new TypeReference<List<ObjectNode>>() {});
            }
        } catch (Exception e) {
            log.warn("⚠️ No se pudo leer el historial de Redis para {}: {}", sessionId, e.getMessage());
        }
        return new ArrayList<>();
    }

    private void saveHistoryToRedis(String sessionId, List<ObjectNode> history) {
        try {
            // Guardamos solo los últimos 10 mensajes para no saturar tokens
            if (history.size() > 10) {
                history = history.subList(history.size() - 10, history.size());
            }
            redisTemplate.opsForValue().set(REDIS_PREFIX + sessionId, history, SESSION_TTL);
        } catch (Exception e) {
            log.error("⚠️ Error guardando en Redis: {}", e.getMessage());
        }
    }
}
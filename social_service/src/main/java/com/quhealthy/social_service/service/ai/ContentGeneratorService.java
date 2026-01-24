package com.quhealthy.social_service.service.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.auth.oauth2.GoogleCredentials;
import com.quhealthy.social_service.dto.ai.AiTextRequest;
import com.quhealthy.social_service.dto.ai.AiTextResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    
    // Cliente HTTP nativo Java 21
    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    // 🚀 MODELO: Gemini 3 Flash Preview
    // Nota: Si este modelo sigue restringido por lista blanca, cambiar a "gemini-2.0-flash-exp"
    private static final String MODEL_NAME = "gemini-3-flash-preview"; 
    
    // URL Base (Sin ?key=...)
    private static final String API_URL_TEMPLATE = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";
    
    private static final String REDIS_PREFIX = "chat:history:";
    private static final Duration SESSION_TTL = Duration.ofMinutes(30);

    public AiTextResponse generatePostText(AiTextRequest request) throws Exception {
        String sessionId = (request.getSessionId() != null && !request.getSessionId().isEmpty()) 
                ? request.getSessionId() 
                : UUID.randomUUID().toString();

        log.info("✨ Iniciando generación OAUTH2 con {} | Sesión: {}", MODEL_NAME, sessionId);

        // 1. Obtener Token OAuth2 (Identidad de Cloud Run)
        // Esto reemplaza a la API Key. Es "Enterprise Grade Security".
        GoogleCredentials credentials = GoogleCredentials.getApplicationDefault()
                .createScoped("https://www.googleapis.com/auth/cloud-platform");
        credentials.refreshIfExpired();
        String accessToken = credentials.getAccessToken().getTokenValue();

        // 2. Construir Historial y Prompt (Igual que antes)
        ArrayNode contentsArray = objectMapper.createArrayNode();
        List<ObjectNode> historyList = loadHistoryFromRedis(sessionId);

        for (ObjectNode msg : historyList) {
            contentsArray.add(msg);
        }

        String engineeredPrompt = buildPrompt(request);
        ObjectNode currentUserMessage = objectMapper.createObjectNode();
        currentUserMessage.put("role", "user");
        currentUserMessage.putArray("parts").addObject().put("text", engineeredPrompt);
        contentsArray.add(currentUserMessage);

        ObjectNode rootPayload = objectMapper.createObjectNode();
        rootPayload.set("contents", contentsArray);
        ObjectNode generationConfig = rootPayload.putObject("generationConfig");
        generationConfig.put("temperature", 0.7);
        generationConfig.put("maxOutputTokens", 800);

        // 3. Request HTTP con Header Authorization
        String endpoint = String.format(API_URL_TEMPLATE, MODEL_NAME);
        
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + accessToken) // 👈 AQUÍ ESTÁ LA MAGIA
                .header("x-goog-user-project", getProjectId(credentials)) // Ayuda con quotas
                .POST(HttpRequest.BodyPublishers.ofString(rootPayload.toString(), StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

        
        // 🛑 AQUÍ ESTÁ EL CAMBIO: MODO RAW ERROR
        if (response.statusCode() != 200) {
            log.error("❌ Error CRUDO de Google AI (Status: {}): {}", response.statusCode(), response.body());
            
            // Eliminamos mi mensaje personalizado.
            // Ahora lanzamos el body exacto que mandó Google.
            throw new RuntimeException(response.body());
        }


        // 5. Parsear Respuesta
        String generatedText = extractTextFromResponse(response.body());

        // 6. Guardar en Redis
        historyList.add(currentUserMessage);
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

    private String getProjectId(GoogleCredentials credentials) {
        // Intenta obtener el Project ID para el header de quota
        if (credentials instanceof com.google.auth.ServiceAccountSigner) {
            return ((com.google.auth.ServiceAccountSigner) credentials).getAccount();
        }
        return System.getenv("GOOGLE_CLOUD_PROJECT"); // Fallback a variable de entorno
    }

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

    private List<ObjectNode> loadHistoryFromRedis(String sessionId) {
        try {
            Object cached = redisTemplate.opsForValue().get(REDIS_PREFIX + sessionId);
            if (cached != null) {
                return objectMapper.convertValue(cached, new TypeReference<List<ObjectNode>>() {});
            }
        } catch (Exception e) {
            log.warn("⚠️ No se pudo leer el historial de Redis: {}", e.getMessage());
        }
        return new ArrayList<>();
    }

    private void saveHistoryToRedis(String sessionId, List<ObjectNode> history) {
        try {
            if (history.size() > 10) {
                history = history.subList(history.size() - 10, history.size());
            }
            redisTemplate.opsForValue().set(REDIS_PREFIX + sessionId, history, SESSION_TTL);
        } catch (Exception e) {
            log.error("⚠️ Error guardando en Redis: {}", e.getMessage());
        }
    }
}
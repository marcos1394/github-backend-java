package com.quhealthy.social_service.config;

import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.web.context.WebApplicationContext;

import java.io.IOException;

@Configuration
public class AiConfig {

    @Value("${spring.cloud.gcp.project-id}")
    private String projectId;

    @Value("${spring.cloud.gcp.location:us-central1}") // Gemini 3 suele estar en us-central1
    private String location;

    /**
     * Cliente principal de Vertex AI.
     * Es pesado, así que lo creamos como Bean Singleton (uno para toda la app).
     */
    @Bean
    public VertexAI vertexAI() throws IOException {
        return new VertexAI(projectId, location);
    }

    /**
     * Modelo para TEXTO (Gemini 3 Flash).
     * Usamos Prototype (Scope) por si queremos cambiar configs dinámicamente, 
     * pero por ahora Singleton está bien para el modelo base.
     */
    @Bean
    public GenerativeModel textModel(VertexAI vertexAI) {
        // "gemini-3-flash-preview" es el nombre del modelo según la doc
        return new GenerativeModel("gemini-3-flash-preview", vertexAI);
    }
    
    /**
     * Modelo para IMAGEN (Gemini 3 Pro Image).
     */
    @Bean
    public GenerativeModel imageModel(VertexAI vertexAI) {
        return new GenerativeModel("gemini-3-pro-image-preview", vertexAI);
    }

    /**
     * Configuración de Redis para guardar objetos complejos (JSON).
     * Esto guardará el historial de chat (ChatSession) serializado.
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        // Las llaves son Strings (ej: "chat:12345")
        template.setKeySerializer(new StringRedisSerializer());
        
        // Los valores son JSON (Historial completo con firmas)
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        
        return template;
    }
}
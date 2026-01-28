package com.quhealthy.review_service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    /**
     * Definimos el ObjectMapper explícitamente para asegurar que esté disponible
     * para el ReviewEventPublisher y configuramos el manejo de fechas.
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        
        // 1. Módulo para soportar LocalDateTime (Java 8+)
        mapper.registerModule(new JavaTimeModule());
        
        // 2. Desactivar timestamps numéricos para que las fechas sean legibles (ISO-8601)
        // Ejemplo: "2026-01-27T10:00:00" en lugar de [2026, 1, 27, 10, 0]
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        return mapper;
    }
}
package com.quhealthy.notification_service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class AppConfig {

    /**
     * ✅ ObjectMapper Configurado (Vital para Pub/Sub)
     * Este Bean es el encargado de convertir los mensajes JSON que llegan
     * de Google Pub/Sub en Objetos Java, manejando correctamente las fechas.
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        
        // Módulo vital para manejar LocalDateTime (Java 8 Time API)
        mapper.registerModule(new JavaTimeModule());
        
        // Para que las fechas se guarden/lean como texto ISO-8601 ("2023-10-05T14:30:00")
        // y no como un array de números [2023, 10, 5, 14, 30]
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        return mapper;
    }
}
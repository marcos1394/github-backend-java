package com.quhealthy.onboarding_service.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestTemplate;

@Configuration
public class AppConfig {

    /**
     * Configuración JSON Robustecida (Homologada con Notification Service)
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // Manejo correcto de fechas (Java 8 LocalDateTime)
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // ✅ CRÍTICO: Ignora campos desconocidos para evitar errores al deserializar
        // respuestas de APIs externas (Google/SAT) o eventos de otros microservicios.
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        return mapper;
    }

    /**
     * Cliente HTTP para consumo de APIs Externas.
     * Lo usaremos en este servicio para:
     * 1. Google Places Proxy (Validar direcciones).
     * 2. Validadores de Cédula/SAT (APIMarket/Nubarium).
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
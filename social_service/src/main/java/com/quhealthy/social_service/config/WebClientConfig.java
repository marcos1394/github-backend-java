package com.quhealthy.social_service.config;

import com.fasterxml.jackson.databind.ObjectMapper; // 👈 Asegúrate de este import
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient webClient() {
        return WebClient.builder()
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    // 👇 ESTO ES LO NUEVO QUE SOLUCIONA TU ERROR 👇
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // Registramos el módulo para manejar fechas de Java 8 (LocalDateTime, etc.)
        mapper.registerModule(new JavaTimeModule());
        // No fallar si el JSON trae campos extra que no conocemos
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        // Formatear fechas como Strings ISO-8601 en lugar de arrays numéricos
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        return mapper;
    }
}
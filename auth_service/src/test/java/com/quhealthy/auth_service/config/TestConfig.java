package com.quhealthy.auth_service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule; // Para fechas Java 8
import com.google.api.gax.core.CredentialsProvider;
import com.google.api.gax.core.NoCredentialsProvider;
import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

@TestConfiguration
@Profile("test")
public class TestConfig {

    // ✅ 1. Mock de PubSub (El que ya tenías)
    @Bean
    @Primary
    public PubSubTemplate pubSubTemplate() {
        return Mockito.mock(PubSubTemplate.class);
    }

    // ✅ 2. FIX GOOGLE CLOUD: Proveedor de credenciales vacío
    // Esto evita que Spring intente buscar un archivo JSON de credenciales en tu disco
    @Bean
    @Primary
    public CredentialsProvider credentialsProvider() {
        return NoCredentialsProvider.create();
    }

    // ✅ 3. FIX JACKSON: ObjectMapper explícito
    // Esto asegura que siempre haya un ObjectMapper disponible para los tests de controladores
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // Registramos el módulo de tiempo para que LocalDate/LocalDateTime funcionen bien en JSON
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }
}
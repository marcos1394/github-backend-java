package com.quhealthy.catalog_service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.api.gax.core.CredentialsProvider;
import com.google.api.gax.core.NoCredentialsProvider;
import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import com.google.cloud.storage.Storage;
import io.minio.MinioClient;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

/**
 * Configuración de Beans para el entorno de Pruebas (Profile: "test").
 * Objetivo: Mockear infraestructura externa para que el contexto de Spring
 * cargue rápido y sin requerir credenciales reales de GCP/AWS.
 */
@TestConfiguration
@Profile("test")
public class TestConfig {

    // =========================================================================
    // 1. MOCKS DE INFRAESTRUCTURA GOOGLE (GCP)
    // =========================================================================

    /**
     * ✅ GCP CREDENTIALS:
     * Evita que Spring busque "application_default_credentials.json".
     * Vital para CI/CD.
     */
    @Bean
    @Primary
    public CredentialsProvider credentialsProvider() {
        return NoCredentialsProvider.create();
    }

    /**
     * ✅ PUBSUB TEMPLATE:
     * Mockeamos la mensajería. Catalog Service podría escuchar eventos
     * de "User Deleted" o emitir "Product Created" en el futuro.
     */
    @Bean
    @Primary
    public PubSubTemplate pubSubTemplate() {
        return Mockito.mock(PubSubTemplate.class);
    }

    /**
     * ✅ CLOUD STORAGE (GCS):
     * Mockeamos el cliente nativo de Google Storage.
     * Catalog usa esto intensivamente para imágenes de productos.
     */
    @Bean
    @Primary
    public Storage storage() {
        return Mockito.mock(Storage.class);
    }

    // =========================================================================
    // 2. MOCKS DE SERVICIOS EXTERNOS (MinIO)
    // =========================================================================

    /**
     * ✅ MINIO CLIENT:
     * Evita conexión real al puerto 9000 durante los tests de integración.
     */
    @Bean
    @Primary
    public MinioClient minioClient() {
        return Mockito.mock(MinioClient.class);
    }

    // =========================================================================
    // 3. UTILIDADES
    // =========================================================================

    /**
     * ✅ JACKSON OBJECT MAPPER:
     * Configuración estándar para fechas (Java 8 Time).
     * Útil si necesitamos serializar/deserializar JSONs en los tests manualmente.
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }
}
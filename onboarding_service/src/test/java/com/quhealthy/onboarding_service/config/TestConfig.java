package com.quhealthy.onboarding_service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.api.gax.core.CredentialsProvider;
import com.google.api.gax.core.NoCredentialsProvider;
import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import com.google.cloud.storage.Storage;
import com.google.maps.GeoApiContext;
import com.quhealthy.onboarding_service.event.OnboardingEventPublisher;
import com.quhealthy.onboarding_service.service.integration.GeminiKycService;
import com.quhealthy.onboarding_service.service.integration.GooglePlacesService;
import com.quhealthy.onboarding_service.service.storage.StorageService;
import io.minio.MinioClient;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

@TestConfiguration
@Profile("test") // Se activa con @ActiveProfiles("test")
public class TestConfig {

    // =========================================================================
    // 1. MOCKS DE INFRAESTRUCTURA GOOGLE (GCP)
    // =========================================================================

    /**
     * ✅ GCP CREDENTIALS:
     * Evita que Spring busque "application_default_credentials.json".
     * Vital para que los tests corran en CI/CD sin credenciales reales.
     */
    @Bean
    @Primary
    public CredentialsProvider credentialsProvider() {
        return NoCredentialsProvider.create();
    }

    /**
     * ✅ PUBSUB TEMPLATE (Infraestructura):
     * Mockeamos el template de bajo nivel.
     */
    @Bean
    @Primary
    public PubSubTemplate pubSubTemplate() {
        return Mockito.mock(PubSubTemplate.class);
    }

    /**
     * ✅ CLOUD STORAGE (GCS):
     * Mockeamos el cliente nativo de Google Storage.
     */
    @Bean
    @Primary
    public Storage storage() {
        return Mockito.mock(Storage.class);
    }

    // =========================================================================
    // 2. MOCKS DE SERVICIOS DE NEGOCIO (SOLUCIÓN ERROR CONTEXT LOAD)
    // =========================================================================

    /**
     * 🚨 SOLUCIÓN DEL ERROR: OnboardingEventPublisher
     * Como desactivamos PubSub en application-test.yml (enabled=false),
     * el Bean real no se crea. Aquí inyectamos un Mock para que KycService no falle.
     */
    @Bean
    @Primary
    public OnboardingEventPublisher onboardingEventPublisher() {
        return Mockito.mock(OnboardingEventPublisher.class);
    }

    /**
     * ✅ GEMINI SERVICE MOCK:
     * Evitamos llamadas a Vertex AI real durante el levantamiento del contexto.
     */
    @Bean
    @Primary
    public GeminiKycService geminiKycService() {
        return Mockito.mock(GeminiKycService.class);
    }

    /**
     * ✅ STORAGE SERVICE MOCK:
     * Mockeamos la capa de abstracción de almacenamiento (MinIO/GCP).
     */
    @Bean
    @Primary
    public StorageService storageService() {
        return Mockito.mock(StorageService.class);
    }

    /**
     * ✅ GOOGLE PLACES SERVICE MOCK:
     * Mockeamos el servicio de mapas.
     */
    @Bean
    @Primary
    public GooglePlacesService googlePlacesService() {
        return Mockito.mock(GooglePlacesService.class);
    }

    // =========================================================================
    // 3. MOCKS DE SERVICIOS EXTERNOS (MinIO y Maps Client)
    // =========================================================================

    /**
     * ✅ MINIO CLIENT:
     * Evita conexión real al puerto 9000.
     */
    @Bean
    @Primary
    public MinioClient minioClient() {
        return Mockito.mock(MinioClient.class);
    }

    /**
     * ✅ GOOGLE MAPS CONTEXT:
     * El `GooglePlacesService` requiere un GeoApiContext.
     */
    @Bean
    @Primary
    public GeoApiContext geoApiContext() {
        return Mockito.mock(GeoApiContext.class);
    }

    // =========================================================================
    // 4. UTILIDADES
    // =========================================================================

    /**
     * ✅ JACKSON OBJECT MAPPER:
     * Configuración estándar para fechas (Java 8 Time).
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }
}
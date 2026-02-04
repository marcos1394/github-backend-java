package com.quhealthy.notification_service.config;

// =========================================================================
// 📦 IMPORTACIONES (Compatibles con Spring Boot 3/4)
// =========================================================================
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.api.gax.core.CredentialsProvider;
import com.google.api.gax.core.NoCredentialsProvider;
import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import com.google.firebase.messaging.FirebaseMessaging;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.integration.channel.DirectChannel; // ✅ VITAL: Para canales dummy
import org.springframework.messaging.MessageChannel;         // ✅ VITAL: Interfaz de canales

@TestConfiguration
@Profile("test") // Solo se carga cuando el perfil activo es 'test'
public class TestConfig {

    // =========================================================================
    // 1. MOCKS DE INFRAESTRUCTURA (Evitan conexiones reales a Nube)
    // =========================================================================

    /**
     * ✅ GCP CREDENTIALS:
     * Evita que Spring Cloud GCP busque el archivo "application_default_credentials.json".
     * Sin esto, el test falla si no tienes gcloud configurado en la máquina.
     */
    @Bean
    @Primary
    public CredentialsProvider credentialsProvider() {
        return NoCredentialsProvider.create();
    }

    /**
     * ✅ PUBSUB TEMPLATE:
     * Aunque en este servicio mayormente escuchamos, este bean evita cualquier
     * intento accidental de publicación o conexión al arrancar el contexto.
     */
    @Bean
    @Primary
    public PubSubTemplate pubSubTemplate() {
        return Mockito.mock(PubSubTemplate.class);
    }

    /**
     * ✅ FIREBASE MESSAGING (Exclusivo Notification Service):
     * Vital. El FirebaseConfig real intenta conectar con Google al inicio.
     * Aquí inyectamos un Mock para que el servicio arranque sin errores de autenticación.
     */
    @Bean
    @Primary
    public FirebaseMessaging firebaseMessaging() {
        return Mockito.mock(FirebaseMessaging.class);
    }

    // =========================================================================
    // 2. UTILIDADES (JACKSON / JSON)
    // =========================================================================

    /**
     * ✅ JACKSON OBJECT MAPPER:
     * Configurado programáticamente para evitar el error "write-dates-as-timestamps".
     * Registra JavaTimeModule para manejar LocalDateTime correctamente.
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // Registra el módulo para manejar LocalDate, LocalDateTime, ZonedDateTime
        // Esto es esencial en Jackson 3 / Spring Boot 4
        mapper.registerModule(new JavaTimeModule());

        return mapper;
    }

    // =========================================================================
    // 3. CANALES DUMMY (FIX PARA PUBSUB)
    // =========================================================================
    // Como PubSubConfig está desactivado en los tests (enabled=false),
    // necesitamos crear manualmente estos canales en memoria para que el
    // NotificationEventListener pueda inyectarlos sin fallar.

    @Bean("accountEventInputChannel")
    public MessageChannel accountEventInputChannel() {
        return new DirectChannel();
    }

    @Bean("appointmentEventInputChannel")
    public MessageChannel appointmentEventInputChannel() {
        return new DirectChannel();
    }
}
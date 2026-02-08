package com.quhealthy.catalog_service.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.spring.pubsub.support.BasicAcknowledgeablePubsubMessage;
import com.google.cloud.spring.pubsub.support.GcpPubSubHeaders;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CatalogEventListenerTest {

    private CatalogEventListener listener;
    private ObjectMapper objectMapper;

    @Mock
    private BasicAcknowledgeablePubsubMessage mockPubSubMessage;

    @BeforeEach
    void setUp() {
        // Usamos un ObjectMapper real porque no queremos mockear el parseo de JSON,
        // queremos probar que realmente funcione.
        objectMapper = new ObjectMapper();
        listener = new CatalogEventListener(objectMapper);
    }

    // ========================================================================
    // ✅ TEST: PROCESAMIENTO EXITOSO (ACK)
    // ========================================================================

    @Test
    @DisplayName("Debe procesar PLAN_DOWNGRADED y hacer ACK")
    void handleMessage_ShouldAck_WhenPlanDowngraded() {
        // GIVEN
        String jsonPayload = """
            {
                "eventType": "PLAN_DOWNGRADED",
                "userId": 100,
                "timestamp": "2026-01-01T12:00:00"
            }
        """;
        Message<?> message = createMessage(jsonPayload);

        // WHEN
        // Invocamos directamente el manejador que devuelve el Bean
        listener.messageReceiver().handleMessage(message);

        // THEN
        // Verificamos que se llamó a ack() (confirmar mensaje)
        verify(mockPubSubMessage, times(1)).ack();
        // Verificamos que NO se llamó a nack() (rechazar)
        verify(mockPubSubMessage, never()).nack();
    }

    @Test
    @DisplayName("Debe procesar USER_DELETED y hacer ACK")
    void handleMessage_ShouldAck_WhenUserDeleted() {
        // GIVEN
        String jsonPayload = """
            {
                "eventType": "USER_DELETED",
                "userId": 999
            }
        """;
        Message<?> message = createMessage(jsonPayload);

        // WHEN
        listener.messageReceiver().handleMessage(message);

        // THEN
        verify(mockPubSubMessage, times(1)).ack();
        verify(mockPubSubMessage, never()).nack();
    }

    @Test
    @DisplayName("Debe ignorar eventos desconocidos pero hacer ACK (para sacarlos de la cola)")
    void handleMessage_ShouldAck_WhenUnknownEvent() {
        // GIVEN
        String jsonPayload = """
            {
                "eventType": "UNKNOWN_EVENT_TYPE",
                "someData": "test"
            }
        """;
        Message<?> message = createMessage(jsonPayload);

        // WHEN
        listener.messageReceiver().handleMessage(message);

        // THEN
        // Aunque no hagamos nada con él, debemos hacer ACK para que PubSub no lo reenvíe infinitamente.
        verify(mockPubSubMessage, times(1)).ack();
        verify(mockPubSubMessage, never()).nack();
    }

    // ========================================================================
    // ❌ TEST: MANEJO DE ERRORES (NACK)
    // ========================================================================

    @Test
    @DisplayName("Debe hacer NACK si el JSON es inválido")
    void handleMessage_ShouldNack_WhenJsonIsMalformed() {
        // GIVEN
        String malformedJson = "{ evento: ESTO NO ES JSON VALIDO }";
        Message<?> message = createMessage(malformedJson);

        // WHEN
        listener.messageReceiver().handleMessage(message);

        // THEN
        // Como falló el objectMapper.readValue(), debe caer en el catch y hacer nack()
        verify(mockPubSubMessage, times(1)).nack();
        verify(mockPubSubMessage, never()).ack();
    }

    @Test
    @DisplayName("No debe hacer nada si el header ORIGINAL_MESSAGE es nulo")
    void handleMessage_ShouldDoNothing_WhenHeaderMissing() {
        // GIVEN
        // Creamos un mensaje SIN el header de GCP
        Message<?> message = MessageBuilder.withPayload("data").build();

        // WHEN
        listener.messageReceiver().handleMessage(message);

        // THEN
        verifyNoInteractions(mockPubSubMessage);
    }

    // ========================================================================
    // 🛠️ HELPER
    // ========================================================================

    private Message<?> createMessage(String jsonPayload) {
        // 1. Crear el mensaje interno de PubSub (Protobuf)
        PubsubMessage internalMessage = PubsubMessage.newBuilder()
                .setData(ByteString.copyFromUtf8(jsonPayload))
                .build();

        // 2. Configurar el Mock del wrapper de Spring GCP
        when(mockPubSubMessage.getPubsubMessage()).thenReturn(internalMessage);

        // 3. Crear el mensaje de Spring Integration con el Header
        return MessageBuilder.withPayload(jsonPayload) // El payload aquí es irrelevante, el listener lee del header
                .setHeader(GcpPubSubHeaders.ORIGINAL_MESSAGE, mockPubSubMessage)
                .build();
    }
}
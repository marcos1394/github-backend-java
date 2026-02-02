package com.quhealthy.auth_service.integration.pubsub;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import com.quhealthy.auth_service.event.UserEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PubSubUserEventPublisherTest {

    @Mock
    private PubSubTemplate pubSubTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private PubSubUserEventPublisher publisher;

    private final String TOPIC_NAME = "test-topic";

    @BeforeEach
    void setUp() {
        // Como 'topicName' se inyecta con @Value, usamos ReflectionTestUtils para asignarlo en el test unitario
        ReflectionTestUtils.setField(publisher, "topicName", TOPIC_NAME);
    }

    @Test
    @DisplayName("Should generate EventId, serialize and publish successfully")
    void publish_Success() throws JsonProcessingException {
        // Arrange
        UserEvent event = UserEvent.builder()
                .userId(1L)
                .eventType("TEST_EVENT")
                .build(); // Nota: eventId es null aquí

        String jsonPayload = "{\"eventType\":\"TEST_EVENT\"}";

        when(objectMapper.writeValueAsString(event)).thenReturn(jsonPayload);
        when(pubSubTemplate.publish(anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture("msg-id-123"));

        // Act
        publisher.publish(event);

        // Assert
        // 1. Verificamos que se haya generado un ID aleatorio (lógica de tu clase)
        assertNotNull(event.getEventId());

        // 2. Verificamos que se serializó
        verify(objectMapper).writeValueAsString(event);

        // 3. Verificamos que se publicó en el tópico correcto con el JSON correcto
        verify(pubSubTemplate).publish(eq(TOPIC_NAME), eq(jsonPayload));
    }

    @Test
    @DisplayName("Should handle JsonProcessingException gracefully")
    void publish_JsonError() throws JsonProcessingException {
        // Arrange
        UserEvent event = UserEvent.builder().userId(1L).build();

        // Simulamos error de Jackson
        when(objectMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("Error") {});

        // Act
        publisher.publish(event);

        // Assert
        // Verificamos que NO se llamó a PubSub (el error fue capturado en el catch)
        verify(pubSubTemplate, never()).publish(anyString(), anyString());
    }

    @Test
    @DisplayName("Should handle generic Exception from PubSub gracefully")
    void publish_PubSubError() throws JsonProcessingException {
        // Arrange
        UserEvent event = UserEvent.builder().userId(1L).build();
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        // Simulamos que PubSub falla
        when(pubSubTemplate.publish(anyString(), anyString()))
                .thenThrow(new RuntimeException("GCP is down"));

        // Act
        publisher.publish(event);

        // Assert
        // Verificamos que se intentó publicar pero la excepción no rompió el test (fue capturada)
        verify(pubSubTemplate).publish(eq(TOPIC_NAME), anyString());
    }
}
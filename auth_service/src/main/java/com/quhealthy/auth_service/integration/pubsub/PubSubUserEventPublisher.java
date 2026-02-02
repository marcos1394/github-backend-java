package com.quhealthy.auth_service.integration.pubsub;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import com.quhealthy.auth_service.event.UserEvent;
import com.quhealthy.auth_service.event.UserEventPublisher; // Importamos la interfaz
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
// Esta implementación SOLO se carga si enabled=true
@ConditionalOnProperty(
        value = "spring.cloud.gcp.pubsub.enabled",
        havingValue = "true"
)
public class PubSubUserEventPublisher implements UserEventPublisher { // Implementa la interfaz

    private final PubSubTemplate pubSubTemplate;
    private final ObjectMapper objectMapper;

    @Value("${gcp.pubsub.topic.user-events}")
    private String topicName;

    @Override // Sobrescribe el método de la interfaz
    public void publish(UserEvent event) {
        try {
            // 1. Trazabilidad
            if (event.getEventId() == null) {
                event.setEventId(UUID.randomUUID().toString());
            }

            // 2. Serializar a JSON (Tu lógica original)
            String jsonMessage = objectMapper.writeValueAsString(event);

            // 3. Publicar
            pubSubTemplate.publish(topicName, jsonMessage);

            log.info("📤 Event published to Pub/Sub [topic={}]: type={}, userId={}",
                    topicName, event.getEventType(), event.getUserId());

        } catch (JsonProcessingException e) {
            log.error("❌ Failed to serialize UserEvent. Dropped. UserId: {}", event.getUserId(), e);
        } catch (Exception e) {
            log.error("❌ Pub/Sub failure: {}. Error: {}", topicName, e.getMessage());
        }
    }
}
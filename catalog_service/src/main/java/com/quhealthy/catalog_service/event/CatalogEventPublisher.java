package com.quhealthy.catalog_service.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class CatalogEventPublisher {

    private final PubSubTemplate pubSubTemplate;
    private final ObjectMapper objectMapper;

    @Value("${gcp.pubsub.topic.catalog:catalog-events-topic}")
    private String topicName;

    public void publish(Long providerId, String eventType, Map<String, Object> payload) {
        try {
            CatalogEvent event = CatalogEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType(eventType)
                    .providerId(providerId)
                    .payload(payload)
                    .timestamp(LocalDateTime.now())
                    .build();

            String messageJson = objectMapper.writeValueAsString(event);

            pubSubTemplate.publish(topicName, messageJson);

            log.debug("📤 Evento publicado en {}: {} - Provider: {}", topicName, eventType, providerId);

        } catch (Exception e) {
            log.error("❌ Error publicando evento de catálogo: {}", e.getMessage());
            // No lanzamos excepción para no romper la transacción principal (fail-safe)
        }
    }
}
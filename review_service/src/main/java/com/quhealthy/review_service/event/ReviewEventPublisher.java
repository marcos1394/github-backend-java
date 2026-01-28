package com.quhealthy.review_service.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import com.quhealthy.review_service.dto.event.ReviewEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewEventPublisher {

    private final PubSubTemplate pubSubTemplate;
    private final ObjectMapper objectMapper; // Para convertir a JSON

    // Leemos el nombre del tema (topic) desde application.properties
    @Value("${application.events.reviews-topic}")
    private String topicName;

    public void publishEvent(ReviewEvent event) {
        try {
            // 1. Convertir el objeto a JSON String
            String jsonMessage = objectMapper.writeValueAsString(event);

            // 2. Publicar a Google Pub/Sub
            pubSubTemplate.publish(topicName, jsonMessage);

            log.info("📤 [Pub/Sub] Evento publicado en '{}': Type={}, ReviewID={}", 
                    topicName, event.getEventType(), event.getReviewId());

        } catch (Exception e) {
            // Enterprise: Si falla el evento, NO queremos que falle la transacción de la reseña.
            // Solo logueamos el error. La reseña se guardó, solo falló el aviso.
            log.error("❌ Error publicando evento Pub/Sub: {}", e.getMessage());
        }
    }
}
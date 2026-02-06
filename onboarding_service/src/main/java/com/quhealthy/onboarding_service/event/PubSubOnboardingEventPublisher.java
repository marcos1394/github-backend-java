package com.quhealthy.onboarding_service.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        value = "spring.cloud.gcp.pubsub.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class PubSubOnboardingEventPublisher implements OnboardingEventPublisher {

    private final PubSubTemplate pubSubTemplate;
    private final ObjectMapper objectMapper;

    @Value("${gcp.pubsub.topic.onboarding-events}")
    private String topicName;

    @Override
    public void publishStepCompleted(Long userId, String email, String stepName, Map<String, Object> extraData) {
        publishEvent(userId, email, "ONBOARDING_STEP_COMPLETED", stepName, extraData);
    }

    @Override
    public void publishStepRejected(Long userId, String email, String stepName, String reason) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("reason", reason);
        publishEvent(userId, email, "ONBOARDING_STEP_REJECTED", stepName, payload);
    }

    // Método privado auxiliar para no repetir código
    private void publishEvent(Long userId, String email, String eventType, String stepName, Map<String, Object> extraData) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("step", stepName);
            if (extraData != null) {
                payload.putAll(extraData);
            }

            UserEvent event = UserEvent.builder()
                    .eventType(eventType)
                    .userId(userId)
                    .email(email)
                    .role("PROVIDER")
                    .payload(payload)
                    .timestamp(LocalDateTime.now())
                    .build();

            String jsonMessage = objectMapper.writeValueAsString(event);
            pubSubTemplate.publish(topicName, jsonMessage);

            log.info("Evento publicado Pub/Sub [topic={}]: type={}, step={}, userId={}",
                    topicName, eventType, stepName, userId);

        } catch (JsonProcessingException e) {
            log.error("Error serializando UserEvent. UserId: {}", userId, e);
        } catch (Exception e) {
            log.error("Fallo Pub/Sub: {}. Error: {}", topicName, e.getMessage());
        }
    }
}
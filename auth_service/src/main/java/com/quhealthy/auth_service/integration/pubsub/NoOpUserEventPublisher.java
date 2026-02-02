package com.quhealthy.auth_service.integration.pubsub;

import com.quhealthy.auth_service.event.UserEvent;
import com.quhealthy.auth_service.event.UserEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
// Esta implementación se carga si enabled=false O si falta la propiedad
@ConditionalOnProperty(
        value = "spring.cloud.gcp.pubsub.enabled",
        havingValue = "false",
        matchIfMissing = true
)
public class NoOpUserEventPublisher implements UserEventPublisher {

    @Override
    public void publish(UserEvent event) {
        // No hacemos NADA (No Operation), solo un log debug
        log.debug("🟡 [NO-OP] Evento simulado (Pub/Sub deshabilitado): {}", event.getEventType());
    }
}
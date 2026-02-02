package com.quhealthy.auth_service.integration.pubsub;

import com.quhealthy.auth_service.event.UserEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@ExtendWith(MockitoExtension.class)
class NoOpUserEventPublisherTest {

    // No necesitamos inyectar nada porque esta clase no tiene dependencias
    private final NoOpUserEventPublisher publisher = new NoOpUserEventPublisher();

    @Test
    @DisplayName("Should execute without errors (log only)")
    void publish_ShouldDoNothing() {
        // Arrange
        UserEvent event = UserEvent.builder()
                .eventType("TEST_EVENT")
                .build();

        // Act & Assert
        // Solo verificamos que al llamarlo no explote.
        // Esto ejecutará la línea del log.debug(...) cubriendo el código.
        assertDoesNotThrow(() -> publisher.publish(event));
    }
}
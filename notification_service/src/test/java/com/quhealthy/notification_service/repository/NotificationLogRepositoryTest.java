package com.quhealthy.notification_service.repository;

import com.quhealthy.notification_service.config.TestConfig;
import com.quhealthy.notification_service.model.NotificationLog;
import com.quhealthy.notification_service.AbstractIntegrationTest; // 👈 Importamos el padre
import com.quhealthy.notification_service.model.enums.NotificationChannel;
import com.quhealthy.notification_service.model.enums.NotificationStatus;
import com.quhealthy.notification_service.model.enums.TargetRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestConfig.class)
class NotificationLogRepositoryTest extends AbstractIntegrationTest { // 👈 EXTENDS

    // 🚫 NO pongas configuración de Docker aquí. Ya la heredas del padre.

    @Autowired
    private NotificationLogRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    @DisplayName("shouldFindByProviderId: Busca por ID externo")
    void shouldFindByProviderId() {
        String providerId = "msg_resend_123456";

        NotificationLog log = NotificationLog.builder()
                .userId(1L)
                .targetRole(TargetRole.CONSUMER)
                .channel(NotificationChannel.EMAIL)
                .status(NotificationStatus.SENT)
                .createdAt(LocalDateTime.now()) // ✅ 1. Ponemos una fecha temporal para que no falle el primer save
                .providerId(providerId)
                .recipient("test@mail.com")
                .subject("Test")
                .createdAt(LocalDateTime.now())
                .build();

        repository.save(log);

        Optional<NotificationLog> found = repository.findByProviderId(providerId);

        assertThat(found).isPresent();
        assertThat(found.get().getProviderId()).isEqualTo(providerId);
    }

    @Test
    @DisplayName("shouldFindStuckMessages: Busca mensajes pendientes viejos")
    void shouldFindStuckMessages() {
        LocalDateTime now = LocalDateTime.now();
        // Usamos el helper para sortear la fecha automática
        createLog(NotificationStatus.PENDING, now.minusHours(2));
        createLog(NotificationStatus.PENDING, now.minusMinutes(5));
        createLog(NotificationStatus.SENT, now.minusHours(2));

        List<NotificationLog> stuckMessages = repository.findByStatusAndCreatedAtBefore(
                NotificationStatus.PENDING,
                now.minusHours(1)
        );

        assertThat(stuckMessages).hasSize(1);
    }

    private void createLog(NotificationStatus status, LocalDateTime createdAt) {
        NotificationLog log = NotificationLog.builder()
                .userId(1L)
                .targetRole(TargetRole.CONSUMER)
                .channel(NotificationChannel.SMS)
                .recipient("+123456789")
                .subject("Test")
                .status(status)
                .createdAt(LocalDateTime.now()) // poral para pasar la validación NOT NULL
                .build();

        log = repository.save(log);
        log.setCreatedAt(createdAt);
        repository.save(log);
    }
}
package com.quhealthy.notification_service.repository;

import com.quhealthy.notification_service.AbstractIntegrationTest;
import com.quhealthy.notification_service.config.TestConfig; // Importante para Mocks
import com.quhealthy.notification_service.model.Notification;
import com.quhealthy.notification_service.model.enums.NotificationType;
import com.quhealthy.notification_service.model.enums.TargetRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime; // 👈 IMPORTANTE
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestConfig.class)
class NotificationRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private NotificationRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    @DisplayName("Debe persistir y recuperar correctamente el campo JSONB (metadata)")
    void shouldPersistJsonMetadata() {
        // Arrange
        Notification notification = Notification.builder()
                .userId(1L)
                .targetRole(TargetRole.CONSUMER)
                .type(NotificationType.INFO)
                .title("Prueba JSON")
                .message("Cuerpo")
                .isRead(false)
                .createdAt(LocalDateTime.now()) // ✅ FIX 1: Fecha explícita
                .metadata(Map.of(
                        "doctor", "Dr. House",
                        "appointmentId", "12345",
                        "urgent", "true"
                ).toString())
                .build();

        // Act
        Notification saved = repository.save(notification);

        // Assert
        Notification fetched = repository.findById(saved.getId()).orElseThrow();

        assertThat(fetched.getMetadata()).isNotEmpty();
        assertThat(fetched.getMetadata()).contains("doctor");
    }

    @Test
    @DisplayName("findByUserIdAndTargetRole: Debe filtrar por Usuario y Rol específico")
    void shouldFilterByUserAndRole() {
        // Arrange
        createNotification(10L, TargetRole.CONSUMER, "Soy Paciente");
        createNotification(10L, TargetRole.PROVIDER, "Soy Médico");
        createNotification(99L, TargetRole.CONSUMER, "Otro Usuario");

        // Act
        Page<Notification> result = repository.findByUserIdAndTargetRole(
                10L, TargetRole.CONSUMER, PageRequest.of(0, 10));

        // Assert
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("Soy Paciente");
    }

    @Test
    @DisplayName("countBy...: Debe contar solo las notificaciones NO leídas")
    void shouldCountUnreadOnly() {
        // Arrange
        createNotification(5L, TargetRole.ADMIN, "Leída", true);
        createNotification(5L, TargetRole.ADMIN, "No Leída 1", false);
        createNotification(5L, TargetRole.ADMIN, "No Leída 2", false);

        // Act
        long count = repository.countByUserIdAndTargetRoleAndIsReadFalse(5L, TargetRole.ADMIN);

        // Assert
        assertThat(count).isEqualTo(2);
    }

    @Test
    @Transactional
    @DisplayName("markAllAsRead: Debe actualizar solo las del usuario y rol indicados")
    void shouldMarkAllAsReadForSpecificUserAndRole() {
        // Arrange
        createNotification(1L, TargetRole.CONSUMER, "User1 Cons", false);
        createNotification(1L, TargetRole.CONSUMER, "User1 Cons", false);
        createNotification(1L, TargetRole.PROVIDER, "User1 Prov", false);
        createNotification(2L, TargetRole.CONSUMER, "User2 Cons", false);

        // Act
        repository.markAllAsRead(1L, TargetRole.CONSUMER);

        // Assert
        assertThat(repository.countByUserIdAndTargetRoleAndIsReadFalse(1L, TargetRole.CONSUMER)).isEqualTo(0);
        assertThat(repository.countByUserIdAndTargetRoleAndIsReadFalse(1L, TargetRole.PROVIDER)).isEqualTo(1);
        assertThat(repository.countByUserIdAndTargetRoleAndIsReadFalse(2L, TargetRole.CONSUMER)).isEqualTo(1);
    }

    // --- HELPER ---
    private void createNotification(Long userId, TargetRole role, String title) {
        createNotification(userId, role, title, false);
    }

    private void createNotification(Long userId, TargetRole role, String title, boolean isRead) {
        repository.save(Notification.builder()
                .userId(userId)
                .targetRole(role)
                .type(NotificationType.INFO)
                .title(title)
                .message("Msg")
                .isRead(isRead)
                .createdAt(LocalDateTime.now()) // ✅ FIX 2: Fecha explícita aquí también
                .build());
    }
}

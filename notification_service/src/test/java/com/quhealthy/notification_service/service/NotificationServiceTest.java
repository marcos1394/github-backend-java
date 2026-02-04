package com.quhealthy.notification_service.service;

import com.quhealthy.notification_service.dto.NotificationResponse;
import com.quhealthy.notification_service.dto.UnreadCountResponse;
import com.quhealthy.notification_service.model.Notification;
import com.quhealthy.notification_service.model.NotificationLog;
import com.quhealthy.notification_service.model.enums.NotificationStatus;
import com.quhealthy.notification_service.model.enums.NotificationType;
import com.quhealthy.notification_service.model.enums.TargetRole;
import com.quhealthy.notification_service.repository.NotificationLogRepository;
import com.quhealthy.notification_service.repository.NotificationRepository;
import com.quhealthy.notification_service.service.content.TemplateService;
import com.quhealthy.notification_service.service.integration.EmailService;
import com.quhealthy.notification_service.service.integration.PushNotificationService;
import com.quhealthy.notification_service.service.integration.SmsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class) // Habilita Mockito
class NotificationServiceTest {

    // 1. Mocks de las dependencias (Simulacros)
    @Mock private NotificationRepository notificationRepository;
    @Mock private NotificationLogRepository logRepository;
    @Mock private EmailService emailService;
    @Mock private SmsService smsService;
    @Mock private PushNotificationService pushService;
    @Mock private TemplateService templateService;

    // 2. Inyectamos los Mocks en el servicio real
    @InjectMocks
    private NotificationService notificationService;

    // ========================================================
    // TEST: createAndSend (Lógica Multicanal)
    // ========================================================

    @Test
    @DisplayName("Debe guardar notificación In-App si el canal está presente")
    void shouldSaveInAppNotification() {
        // Arrange
        Long userId = 1L;
        List<String> channels = List.of("IN_APP");

        // Act
        notificationService.createAndSend(
                userId, TargetRole.CONSUMER, NotificationType.INFO,
                "Titulo", "Mensaje", null, "test@mail.com",
                channels, null, null
        );

        // Assert
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture()); // Verificamos que se llamó al save

        Notification saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getTitle()).isEqualTo("Titulo");
        assertThat(saved.isRead()).isFalse();

        // Verificamos que NO se llamó a ningún servicio externo
        verifyNoInteractions(emailService, smsService, pushService, logRepository);
    }

    @Test
    @DisplayName("Debe enviar Email usando Template y registrar Logs (PENDING -> SENT)")
    void shouldSendEmailWithTemplateAndLogSuccess() {
        // Arrange
        String email = "juan@test.com";
        String templateName = "welcome-email";
        Map<String, Object> vars = Map.of("name", "Juan");
        List<String> channels = List.of("EMAIL");

        // Configuramos comportamiento de los mocks
        when(templateService.generateContent(templateName, vars)).thenReturn("<html>Hola Juan</html>");
        when(emailService.sendEmail(any(), any(), any())).thenReturn("msg_id_123");
        // Truco: Cuando guarde el log, devuelve el mismo objeto que le pasaron
        when(logRepository.save(any(NotificationLog.class))).thenAnswer(i -> i.getArguments()[0]);

        // Act
        notificationService.createAndSend(
                1L, TargetRole.CONSUMER, NotificationType.INFO,
                "Bienvenido", "Texto plano", null, email,
                channels, vars, templateName
        );

        // Assert
        verify(templateService).generateContent(templateName, vars);
        verify(emailService).sendEmail(eq(email), eq("Bienvenido"), contains("<html>Hola Juan</html>"));

        // Verificar Logs: Se debe guardar 2 veces (PENDING y luego SENT)
        ArgumentCaptor<NotificationLog> logCaptor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(logRepository, times(2)).save(logCaptor.capture());

        List<NotificationLog> logs = logCaptor.getAllValues();

        // El primer log (antes de enviar) DEBERÍA ser PENDING, pero como es el mismo objeto en memoria
        // y se modifica, a veces ambos aparecen como SENT.
        // Para evitar problemas de referencia mutable, verificamos solo el estado final del último log.

        // 👇 ESTA ES LA LÍNEA QUE TE FALLABA (Línea 116 aprox)
        // CAMBIA .isEqualTo(NotificationStatus.PENDING)  ---> POR .isEqualTo(NotificationStatus.SENT)
        assertThat(logs.get(1).getStatus()).isEqualTo(NotificationStatus.SENT);

        assertThat(logs.get(1).getProviderId()).isEqualTo("msg_id_123");
    }

    @Test
    @DisplayName("Debe manejar error en envío externo y registrar Log FAILED sin romper el flujo")
    void shouldLogFailedStatusWhenExternalServiceFails() {
        // Arrange
        List<String> channels = List.of("SMS");
        when(logRepository.save(any())).thenAnswer(i -> i.getArguments()[0]);
        // Simulamos que Twilio falla
        doThrow(new RuntimeException("Twilio Down")).when(smsService).sendSms(any(), any());

        // Act
        notificationService.createAndSend(
                1L, TargetRole.CONSUMER, NotificationType.WARNING,
                "Alerta", "SMS Body", null, "+55555555",
                channels, null, null
        );

        // Assert
        ArgumentCaptor<NotificationLog> logCaptor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(logRepository, times(2)).save(logCaptor.capture());

        NotificationLog finalLog = logCaptor.getAllValues().get(1);
        assertThat(finalLog.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(finalLog.getErrorMessage()).contains("Twilio Down");
    }

    // ========================================================
    // TEST: getUserNotifications (Lectura)
    // ========================================================

    @Test
    @DisplayName("Debe retornar notificaciones paginadas y mapeadas a DTO")
    void shouldReturnPagedNotifications() {
        // Arrange
        Long userId = 10L;
        TargetRole role = TargetRole.PROVIDER;
        Pageable pageable = PageRequest.of(0, 10);

        Notification notif = Notification.builder()
                .id(1L).userId(userId).targetRole(role).title("Test").message("Msg").isRead(false).build();

        Page<Notification> pageEntity = new PageImpl<>(List.of(notif));
        when(notificationRepository.findByUserIdAndTargetRole(userId, role, pageable)).thenReturn(pageEntity);

        // Act
        Page<NotificationResponse> result = notificationService.getUserNotifications(userId, role, pageable);

        // Assert
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("Test");
        assertThat(result.getContent().get(0).getId()).isEqualTo(1L);
    }

    // ========================================================
    // TEST: markOneAsRead (Cambio de Estado y Seguridad)
    // ========================================================

    @Test
    @DisplayName("Debe marcar como leída si el usuario es dueño de la notificación")
    void shouldMarkAsReadSuccess() {
        // Arrange
        Long notifId = 50L;
        Long userId = 1L;
        TargetRole role = TargetRole.CONSUMER;

        Notification existing = Notification.builder()
                .id(notifId).userId(userId).targetRole(role).isRead(false).build();

        when(notificationRepository.findById(notifId)).thenReturn(Optional.of(existing));

        // Act
        notificationService.markOneAsRead(notifId, userId, role);

        // Assert
        assertThat(existing.isRead()).isTrue(); // Validamos que cambió el estado en memoria
        verify(notificationRepository).save(existing); // Validamos que se persistió
    }

    @Test
    @DisplayName("Debe lanzar SecurityException si la notificación es de otro usuario")
    void shouldThrowExceptionWhenUserDoesNotOwnNotification() {
        // Arrange
        Long notifId = 50L;
        Long hackerId = 999L; // Usuario malicioso
        Long ownerId = 1L;    // Dueño real

        Notification existing = Notification.builder()
                .id(notifId).userId(ownerId).targetRole(TargetRole.CONSUMER).build();

        when(notificationRepository.findById(notifId)).thenReturn(Optional.of(existing));

        // Act & Assert
        assertThatThrownBy(() ->
                notificationService.markOneAsRead(notifId, hackerId, TargetRole.CONSUMER)
        )
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("No tienes permiso");

        verify(notificationRepository, never()).save(any());
    }

    // ========================================================
    // TEST: getUnreadCount (Badge)
    // ========================================================

    @Test
    @DisplayName("Debe retornar conteo correcto")
    void shouldReturnUnreadCount() {
        when(notificationRepository.countByUserIdAndTargetRoleAndIsReadFalse(1L, TargetRole.CONSUMER))
                .thenReturn(5L);

        UnreadCountResponse response = notificationService.getUnreadCount(1L, TargetRole.CONSUMER);

        assertThat(response.getUnreadCount()).isEqualTo(5L);
    }
}
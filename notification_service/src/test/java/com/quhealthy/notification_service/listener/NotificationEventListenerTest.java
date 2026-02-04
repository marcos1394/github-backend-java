package com.quhealthy.notification_service.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.cloud.spring.pubsub.support.BasicAcknowledgeablePubsubMessage;
import com.google.cloud.spring.pubsub.support.GcpPubSubHeaders;
import com.quhealthy.notification_service.model.enums.NotificationType;
import com.quhealthy.notification_service.model.enums.TargetRole;
import com.quhealthy.notification_service.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.GenericMessage;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationEventListenerTest {

    private ObjectMapper objectMapper;

    @Mock private NotificationService notificationService;
    @Mock private BasicAcknowledgeablePubsubMessage pubSubMessage;

    private NotificationEventListener listener;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        listener = new NotificationEventListener(objectMapper, notificationService);
    }

    // ========================================================
    // 🧪 TEST DE TODOS LOS CASOS DE USUARIO (Switch Completo)
    // ========================================================

    @Test
    void shouldHandleEmailVerification() {
        String json = createPayload("EMAIL_VERIFICATION_REQUIRED", Map.of("token", "XYZ-123", "name", "Juan"));
        listener.accountMessageHandler().handleMessage(buildMessage(json));

        verify(notificationService).createAndSend(
                anyLong(), eq(TargetRole.CONSUMER), eq(NotificationType.WARNING),
                contains("Verifica"), contains("XYZ-123"), any(), anyString(),
                eq(List.of("EMAIL")), anyMap(), eq("email-verification")
        );
        verify(pubSubMessage).ack();
    }

    @Test
    void shouldHandlePasswordReset() {
        String json = createPayload("PASSWORD_RESET_REQUESTED", Map.of("token", "RESET-999"));
        listener.accountMessageHandler().handleMessage(buildMessage(json));

        verify(notificationService).createAndSend(
                anyLong(), any(), eq(NotificationType.WARNING),
                contains("Recuperar"), anyString(), any(), anyString(),
                eq(List.of("EMAIL")), anyMap(), eq("password-reset")
        );
    }

    @Test
    void shouldHandleAccountVerified() {
        String json = createPayload("ACCOUNT_VERIFIED", Map.of());
        listener.accountMessageHandler().handleMessage(buildMessage(json));

        verify(notificationService).createAndSend(
                anyLong(), any(), eq(NotificationType.SUCCESS),
                contains("Verificada"), anyString(), anyString(), anyString(),
                eq(List.of("IN_APP", "EMAIL")), anyMap(), eq("account-verified")
        );
    }

    @Test
    void shouldHandlePasswordChanged() {
        String json = createPayload("PASSWORD_CHANGED", Map.of());
        listener.accountMessageHandler().handleMessage(buildMessage(json));

        verify(notificationService).createAndSend(
                anyLong(), any(), eq(NotificationType.INFO),
                contains("Contraseña"), anyString(), anyString(), anyString(),
                anyList(), anyMap(), eq("password-changed")
        );
    }

    @Test
    void shouldHandleLoginDetected() {
        String json = createPayload("LOGIN_DETECTED", Map.of("device", "iPhone", "ip", "1.1.1.1"));
        listener.accountMessageHandler().handleMessage(buildMessage(json));

        verify(notificationService).createAndSend(
                anyLong(), any(), eq(NotificationType.WARNING),
                contains("Nuevo Inicio"), anyString(), anyString(), anyString(),
                anyList(), anyMap(), eq("new-login")
        );
    }

    @Test
    void shouldHandleAccountLocked() {
        String json = createPayload("ACCOUNT_LOCKED", Map.of());
        listener.accountMessageHandler().handleMessage(buildMessage(json));

        verify(notificationService).createAndSend(
                anyLong(), any(), eq(NotificationType.ERROR),
                contains("Bloqueada"), anyString(), any(), anyString(),
                anyList(), anyMap(), eq("account-locked")
        );
    }

    @Test
    void shouldHandleAccountDeleted() {
        String json = createPayload("ACCOUNT_DELETED", Map.of());
        listener.accountMessageHandler().handleMessage(buildMessage(json));

        verify(notificationService).createAndSend(
                anyLong(), any(), eq(NotificationType.WARNING),
                contains("Eliminada"), anyString(), any(), anyString(),
                anyList(), isNull(), eq("account-deleted")
        );
    }

    // ========================================================
    // 🧪 CASOS DE BORDE (Else / Default)
    // ========================================================

    @Test
    @DisplayName("Debe ignorar PHONE_VERIFICATION si no hay teléfono (Cobertura del 'else')")
    void shouldIgnoreSmsIfPhoneMissing() {
        // Payload sin phoneNumber
        String json = createPayload("PHONE_VERIFICATION_REQUIRED", Map.of("otp", "123"));

        listener.accountMessageHandler().handleMessage(buildMessage(json));

        // Verificamos que NO llamó al servicio (entró al else log.warn)
        verifyNoInteractions(notificationService);
        verify(pubSubMessage).ack();
    }

    @Test
    @DisplayName("Debe ignorar eventos desconocidos (Cobertura del 'default')")
    void shouldIgnoreUnknownEvents() {
        String json = createPayload("EVENTO_RARO_INVENTADO", Map.of());

        listener.accountMessageHandler().handleMessage(buildMessage(json));

        verifyNoInteractions(notificationService);
        verify(pubSubMessage).ack();
    }

    // ========================================================
    // 🧪 EVENTOS DE CITAS (Appointment)
    // ========================================================

    @Test
    void shouldHandleAppointmentCancelled() {
        String json = createAppointmentPayload("CANCELLED_BY_PROVIDER");
        listener.appointmentMessageHandler().handleMessage(buildMessage(json));

        verify(notificationService).createAndSend(
                anyLong(), eq(TargetRole.CONSUMER), eq(NotificationType.ERROR),
                contains("Cancelada"), anyString(), any(), anyString(),
                anyList(), isNull(), isNull()
        );
    }

    @Test
    void shouldHandleAppointmentCompleted() {
        String json = createAppointmentPayload("COMPLETED");
        listener.appointmentMessageHandler().handleMessage(buildMessage(json));

        verify(notificationService).createAndSend(
                anyLong(), eq(TargetRole.CONSUMER), eq(NotificationType.INFO),
                contains("Califica"), anyString(), anyString(), anyString(),
                anyList(), isNull(), isNull()
        );
    }

    // --- HELPERS ---

    private Message<String> buildMessage(String payload) {
        return new GenericMessage<>(payload, Map.of(
                GcpPubSubHeaders.ORIGINAL_MESSAGE, pubSubMessage
        ));
    }

    private String createPayload(String type, Map<String, Object> payloadData) {
        try {
            // Construimos un JSON manual simple que coincida con tu UserEvent
            return objectMapper.writeValueAsString(Map.of(
                    "eventType", type,
                    "userId", 1,
                    "role", "CONSUMER",
                    "email", "test@test.com",
                    "timestamp", "2026-01-01T12:00:00",
                    "payload", payloadData
            ));
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private String createAppointmentPayload(String type) {
        return """
            {
                "type": "%s",
                "appointmentId": 1,
                "consumerId": 10,
                "consumerEmail": "a@a.com",
                "providerId": 20,
                "providerEmail": "b@b.com",
                "timestamp": "2026-01-01T12:00:00"
            }
        """.formatted(type);
    }
}
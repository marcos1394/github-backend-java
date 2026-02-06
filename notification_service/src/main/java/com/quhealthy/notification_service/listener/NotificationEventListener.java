package com.quhealthy.notification_service.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.spring.pubsub.support.BasicAcknowledgeablePubsubMessage;
import com.google.cloud.spring.pubsub.support.GcpPubSubHeaders;
import com.quhealthy.notification_service.event.AppointmentEvent;
import com.quhealthy.notification_service.event.UserEvent;
import com.quhealthy.notification_service.model.enums.NotificationType;
import com.quhealthy.notification_service.model.enums.TargetRole;
import com.quhealthy.notification_service.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.MessageHandler;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;

    // ======================================================
    // 👤 EVENTOS DE USUARIO (Auth Service) - COBERTURA 100%
    // ======================================================
    @Bean
    @ServiceActivator(inputChannel = "accountEventInputChannel")
    public MessageHandler accountMessageHandler() {
        return message -> {
            BasicAcknowledgeablePubsubMessage originalMessage =
                    message.getHeaders().get(GcpPubSubHeaders.ORIGINAL_MESSAGE, BasicAcknowledgeablePubsubMessage.class);

            try {
                String json = (String) message.getPayload();
                log.debug("📩 Evento Auth recibido: {}", json);

                UserEvent event = objectMapper.readValue(json, UserEvent.class);

                // Enrutamos según el tipo de evento de negocio
                switch (event.getEventType()) {

                    // 1. Registro (Bienvenida)
                    case "USER_REGISTERED":
                        handleUserRegistered(event);
                        break;

                    // 2. Verificación de Email (Link con Token)
                    case "EMAIL_VERIFICATION_REQUIRED":
                        handleEmailVerification(event);
                        break;

                    // 3. Verificación de Teléfono (SMS OTP) 👈 TU REQUERIMIENTO DE SMS
                    case "PHONE_VERIFICATION_REQUIRED":
                        handlePhoneVerification(event);
                        break;

                    // 4. Verificación Exitosa (Confirmación)
                    case "ACCOUNT_VERIFIED":
                        handleAccountVerified(event);
                        break;

                    // 5. Solicitud de Reset Password
                    case "PASSWORD_RESET_REQUESTED":
                        handlePasswordResetRequested(event);
                        break;

                    // 6. Contraseña Cambiada (Alerta Seguridad)
                    case "PASSWORD_CHANGED":
                        handlePasswordChanged(event);
                        break;

                    // 7. Nuevo Login (Alerta Seguridad)
                    case "LOGIN_DETECTED":
                        handleLoginDetected(event);
                        break;

                    // 8. Cuenta Bloqueada (Alerta Seguridad)
                    case "ACCOUNT_LOCKED":
                        handleAccountLocked(event);
                        break;

                    // 9. Cuenta Eliminada (Despedida)
                    case "ACCOUNT_DELETED":
                        handleAccountDeleted(event);
                        break;

                    default:
                        log.debug("ℹ️ Evento de usuario no manejado: {}", event.getEventType());
                }

                if (originalMessage != null) originalMessage.ack();

            } catch (Exception e) {
                log.error("❌ Error procesando evento de cuenta: {}", e.getMessage(), e);
                // Hacemos ACK para evitar bucles infinitos con mensajes corruptos.
                if (originalMessage != null) originalMessage.ack();
            }
        };
    }

    // ======================================================
    // 📅 EVENTOS DE CITAS (Appointment Service)
    // ======================================================
    @Bean
    @ServiceActivator(inputChannel = "appointmentEventInputChannel")
    public MessageHandler appointmentMessageHandler() {
        return message -> {
            BasicAcknowledgeablePubsubMessage originalMessage =
                    message.getHeaders().get(GcpPubSubHeaders.ORIGINAL_MESSAGE, BasicAcknowledgeablePubsubMessage.class);

            try {
                String json = (String) message.getPayload();
                AppointmentEvent event = objectMapper.readValue(json, AppointmentEvent.class);

                String type = event.getType() != null ? event.getType().name() : "UNKNOWN";

                switch (type) {
                    case "CREATED":
                        handleAppointmentCreated(event);
                        break;
                    case "CANCELLED_BY_PROVIDER":
                    case "CANCELLED_BY_CONSUMER":
                        handleAppointmentCanceled(event);
                        break;
                    case "COMPLETED":
                        handleAppointmentCompleted(event);
                        break;
                }

                if (originalMessage != null) originalMessage.ack();

            } catch (Exception e) {
                log.error("❌ Error procesando evento de cita", e);
                if (originalMessage != null) originalMessage.ack();
            }
        };
    }

    // =====================================================
    //
    // =
    // 👇 HANDLERS PRIVADOS (Lógica de Mapeo a Templates)
    // ======================================================

    private void handleUserRegistered(UserEvent event) {
        String name = getPayloadValue(event, "name", "Usuario");

        notificationService.createAndSend(
                event.getUserId(), TargetRole.valueOf(event.getRole()), NotificationType.INFO,
                "¡Bienvenido a QuHealthy!", "Gracias por registrarte.", "/profile",
                event.getEmail(), List.of("IN_APP", "EMAIL"),
                Map.of("name", name, "actionLink", "https://quhealthy.org/login"),
                "welcome-email"
        );
    }

    private void handleEmailVerification(UserEvent event) {
        String name = getPayloadValue(event, "name", "Usuario");
        String token = getPayloadValue(event, "token", "ERROR");

        notificationService.createAndSend(
                event.getUserId(), TargetRole.valueOf(event.getRole()), NotificationType.WARNING,
                "Verifica tu Correo", "Código de verificación: " + token, null,
                event.getEmail(), List.of("EMAIL"), // Solo Email
                Map.of(
                        "name", name,
                        "token", token,
                        "verificationLink", "https://quhealthy.org/verify-email?token=" + token
                ),
                "email-verification"
        );
    }

    /**
     * Maneja el envío de SMS OTP.
     * Auth Service debe mandar: { "phoneNumber": "+52...", "otp": "123456" }
     */
    private void handlePhoneVerification(UserEvent event) {
        String phoneNumber = getPayloadValue(event, "phoneNumber", null);
        String otp = getPayloadValue(event, "otp", "");

        if (phoneNumber != null && !phoneNumber.isEmpty()) {
            // No creamos notificación In-App, solo enviamos el SMS directo
            notificationService.createAndSend(
                    event.getUserId(), TargetRole.valueOf(event.getRole()), NotificationType.WARNING,
                    "Código de Verificación", "Tu código QuHealthy es: " + otp, null,
                    phoneNumber, List.of("SMS"), // 👈 Canal SMS Exclusivo
                    null, null // Sin template HTML
            );
        } else {
            log.warn("⚠️ Evento SMS recibido sin número de teléfono. Usuario: {}", event.getUserId());
        }
    }

    private void handleAccountVerified(UserEvent event) {
        notificationService.createAndSend(
                event.getUserId(), TargetRole.valueOf(event.getRole()), NotificationType.SUCCESS,
                "Cuenta Verificada", "Tu cuenta ha sido activada correctamente.", "/dashboard",
                event.getEmail(), List.of("IN_APP", "EMAIL"),
                Map.of("loginLink", "https://quhealthy.org/login"),
                "account-verified"
        );
    }

    private void handlePasswordResetRequested(UserEvent event) {
        String name = getPayloadValue(event, "name", "Usuario");
        String token = getPayloadValue(event, "token", "---");

        notificationService.createAndSend(
                event.getUserId(), TargetRole.valueOf(event.getRole()), NotificationType.WARNING,
                "Recuperar Contraseña", "Usa el código para restablecer tu clave.", null,
                event.getEmail(), List.of("EMAIL"),
                Map.of(
                        "name", name,
                        "token", token,
                        "resetLink", "https://quhealthy.org/reset-password?token=" + token
                ),
                "password-reset"
        );
    }

    private void handlePasswordChanged(UserEvent event) {
        notificationService.createAndSend(
                event.getUserId(), TargetRole.valueOf(event.getRole()), NotificationType.INFO,
                "Contraseña Actualizada", "Tu contraseña fue modificada recientemente.", "/security",
                event.getEmail(), List.of("IN_APP", "EMAIL"),
                Map.of(
                        "date", DateTimeFormatter.ISO_LOCAL_DATE.format(event.getTimestamp()),
                        "supportLink", "https://quhealthy.org/support"
                ),
                "password-changed"
        );
    }

    private void handleLoginDetected(UserEvent event) {
        String name = getPayloadValue(event, "name", "Usuario");

        notificationService.createAndSend(
                event.getUserId(), TargetRole.valueOf(event.getRole()), NotificationType.WARNING,
                "Nuevo Inicio de Sesión", "Acceso detectado desde un dispositivo nuevo.", "/security",
                event.getEmail(), List.of("EMAIL"), // Solo Email por seguridad
                Map.of(
                        "name", name,
                        "time", event.getTimestamp().toString(),
                        "device", getPayloadValue(event, "device", "Desconocido"),
                        "location", getPayloadValue(event, "location", "Ubicación Desconocida"),
                        "ip", getPayloadValue(event, "ip", "0.0.0.0"),
                        "lockAccountLink", "https://quhealthy.org/security/lock-account"
                ),
                "new-login"
        );
    }

    private void handleAccountLocked(UserEvent event) {
        notificationService.createAndSend(
                event.getUserId(), TargetRole.valueOf(event.getRole()), NotificationType.ERROR,
                "Cuenta Bloqueada", "Bloqueo temporal por intentos fallidos.", null,
                event.getEmail(), List.of("EMAIL"),
                Map.of("unlockLink", "https://quhealthy.org/unlock"),
                "account-locked"
        );
    }

    private void handleAccountDeleted(UserEvent event) {
        notificationService.createAndSend(
                event.getUserId(), TargetRole.valueOf(event.getRole()), NotificationType.WARNING,
                "Cuenta Eliminada", "Tu cuenta ha sido eliminada permanentemente.", null,
                event.getEmail(), List.of("EMAIL"),
                null,
                "account-deleted"
        );
    }

    // --- HANDLERS DE CITAS (Simplificados para compilar) ---

    private void handleAppointmentCreated(AppointmentEvent event) {
        notificationService.createAndSend(
                event.getConsumerId(), TargetRole.CONSUMER, NotificationType.SUCCESS,
                "Cita Confirmada", "Tu cita con " + event.getProviderName() + " ha sido agendada.",
                "/dashboard/appointments/" + event.getAppointmentId(),
                event.getConsumerEmail(), List.of("IN_APP", "EMAIL"),
                Map.of("providerName", safe(event.getProviderName()), "date", event.getTimestamp()),
                "appointment-confirmation-consumer"
        );

        notificationService.createAndSend(
                event.getProviderId(), TargetRole.PROVIDER, NotificationType.INFO,
                "Nueva Cita Agendada", "Nuevo paciente: " + event.getConsumerName(),
                "/doctor/calendar/" + event.getAppointmentId(),
                event.getProviderEmail(), List.of("IN_APP", "EMAIL"),
                null, null
        );
    }

    private void handleAppointmentCanceled(AppointmentEvent event) {
        notificationService.createAndSend(
                event.getConsumerId(), TargetRole.CONSUMER, NotificationType.ERROR,
                "Cita Cancelada", "La cita ha sido cancelada.", null,
                event.getConsumerEmail(), List.of("IN_APP", "EMAIL"), null, null
        );
    }

    private void handleAppointmentCompleted(AppointmentEvent event) {
        notificationService.createAndSend(
                event.getConsumerId(), TargetRole.CONSUMER, NotificationType.INFO,
                "¡Califica tu cita!", "¿Cómo estuvo el servicio?",
                "/reviews/rate/" + event.getAppointmentId(),
                event.getConsumerEmail(), List.of("IN_APP", "PUSH_NOTIFICATION"), null, null
        );
    }

    // --- UTILS ---
    private String getPayloadValue(UserEvent event, String key, String defaultValue) {
        if (event.getPayload() == null) return defaultValue;

        Object value = event.getPayload().get(key);

        // Si el valor es null, devolvemos el defaultValue directamente
        // Evitamos String.valueOf(null) que produce "null"
        if (value == null) {
            return defaultValue;
        }

        return String.valueOf(value);
    }

    private String safe(String value) {
        return value != null ? value : "";
    }

}
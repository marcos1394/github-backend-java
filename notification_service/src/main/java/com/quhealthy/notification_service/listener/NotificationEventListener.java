package com.quhealthy.notification_service.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.spring.pubsub.support.BasicAcknowledgeablePubsubMessage;
import com.google.cloud.spring.pubsub.support.GcpPubSubHeaders;
import com.quhealthy.notification_service.event.AppointmentEvent;
import com.quhealthy.notification_service.event.UserAccountDeletedEvent;
import com.quhealthy.notification_service.model.enums.NotificationType;
import com.quhealthy.notification_service.model.enums.TargetRole;
import com.quhealthy.notification_service.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.annotation.ServiceActivator; // 👈 ESTO ANTES FALLABA
import org.springframework.messaging.MessageHandler; // 👈 ESTO ANTES FALLABA
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;

    @Bean
    @ServiceActivator(inputChannel = "accountEventInputChannel")
    public MessageHandler accountMessageHandler() {
        return message -> {
            BasicAcknowledgeablePubsubMessage originalMessage =
                    message.getHeaders().get(GcpPubSubHeaders.ORIGINAL_MESSAGE, BasicAcknowledgeablePubsubMessage.class);
            
            try {
                String json = (String) message.getPayload();
                log.debug("📩 Recibido evento de cuenta: {}", json);

                UserAccountDeletedEvent event = objectMapper.readValue(json, UserAccountDeletedEvent.class);

                String body = "Tu cuenta ha sido eliminada permanentemente. Esperamos verte pronto.";
                
                notificationService.createAndSend(
                        event.getUserId(),
                        TargetRole.valueOf(event.getRole()),
                        NotificationType.WARNING,
                        "Cuenta Eliminada",
                        body,
                        null,
                        event.getEmail(),
                        List.of("EMAIL")
                );

                if (originalMessage != null) originalMessage.ack();

            } catch (Exception e) {
                log.error("❌ Error procesando evento de cuenta", e);
                if (originalMessage != null) originalMessage.ack();
            }
        };
    }

    @Bean
    @ServiceActivator(inputChannel = "appointmentEventInputChannel")
    public MessageHandler appointmentMessageHandler() {
        return message -> {
            BasicAcknowledgeablePubsubMessage originalMessage =
                    message.getHeaders().get(GcpPubSubHeaders.ORIGINAL_MESSAGE, BasicAcknowledgeablePubsubMessage.class);
            
            try {
                String json = (String) message.getPayload();
                log.debug("📩 Recibido evento de cita: {}", json);

                AppointmentEvent event = objectMapper.readValue(json, AppointmentEvent.class);

                switch (event.getEventType()) {
                    case "APPOINTMENT_CREATED":
                        handleAppointmentCreated(event);
                        break;
                    case "APPOINTMENT_CANCELED":
                        handleAppointmentCanceled(event);
                        break;
                    case "APPOINTMENT_COMPLETED":
                        handleAppointmentCompleted(event);
                        break;
                    default:
                        log.warn("Tipo de evento desconocido: {}", event.getEventType());
                }

                if (originalMessage != null) originalMessage.ack();

            } catch (Exception e) {
                log.error("❌ Error procesando evento de cita", e);
                if (originalMessage != null) originalMessage.ack();
            }
        };
    }

    private void handleAppointmentCreated(AppointmentEvent event) {
        notificationService.createAndSend(
            event.getConsumerId(), TargetRole.CONSUMER, NotificationType.SUCCESS,
            "Cita Confirmada", "Tu cita ha sido agendada exitosamente.",
            "/dashboard/appointments/" + event.getAppointmentId(),
            event.getConsumerEmail(), List.of("IN_APP", "EMAIL", "WHATSAPP")
        );

        notificationService.createAndSend(
            event.getProviderId(), TargetRole.PROVIDER, NotificationType.INFO,
            "Nueva Cita Agendada", "Tienes un nuevo paciente en tu agenda.",
            "/doctor/calendar/" + event.getAppointmentId(),
            event.getProviderEmail(), List.of("IN_APP", "EMAIL")
        );
    }

    private void handleAppointmentCanceled(AppointmentEvent event) {
        String msg = "La cita programada ha sido cancelada.";
        notificationService.createAndSend(
            event.getConsumerId(), TargetRole.CONSUMER, NotificationType.ERROR, 
            "Cita Cancelada", msg, null, event.getConsumerEmail(), List.of("IN_APP", "EMAIL")
        );
        notificationService.createAndSend(
            event.getProviderId(), TargetRole.PROVIDER, NotificationType.ERROR, 
            "Cita Cancelada", msg, null, event.getProviderEmail(), List.of("IN_APP")
        );
    }
    
    private void handleAppointmentCompleted(AppointmentEvent event) {
        notificationService.createAndSend(
            event.getConsumerId(), TargetRole.CONSUMER, NotificationType.INFO,
            "¡Cuéntanos tu experiencia!", "Tu cita ha finalizado. ¿Cómo estuvo el servicio?",
            "/reviews/rate/" + event.getAppointmentId(),
            event.getConsumerEmail(), List.of("IN_APP", "EMAIL")
        );
    }
}
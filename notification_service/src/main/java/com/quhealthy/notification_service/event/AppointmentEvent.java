package com.quhealthy.notification_service.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AppointmentEvent {

    private Long appointmentId;

    // IDs de referencia
    private Long providerId;
    private Long consumerId;

    // ✅ Event Enrichment: Datos listos para usar (Emails/Nombres)
    // Así NotificationService no tiene que consultar APIs externas.
    private String providerEmail;
    private String providerName;

    private String consumerEmail;
    private String consumerName;

    private AppointmentEventType type; // CREATED, CANCELED, COMPLETED

    // 🌟 FLEXIBILIDAD: Aquí metemos coordenadas, dirección del consultorio, etc.
    private Map<String, Object> metadata;

    private LocalDateTime timestamp;

    public enum AppointmentEventType {
        CREATED,
        CANCELLED_BY_PROVIDER,
        CANCELLED_BY_CONSUMER,
        RESCHEDULED,
        COMPLETED,
        REMINDER // El scheduler de citas disparará este evento 1 hora antes
    }
}
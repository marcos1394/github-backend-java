package com.quhealthy.notification_service.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentEvent {
    private Long appointmentId;
    private Long providerId;
    private Long consumerId;
    
    // ⚠️ IMPORTANTE: En un sistema Enterprise, el emisor del evento (AppointmentService)
    // debería enriquecer este mensaje con los emails para evitar que tengamos que
    // consultar al Auth Service por cada notificación (Pattern: Event Enrichment).
    private String providerEmail; 
    private String consumerEmail;
    
    private String eventType; // CREATED, CANCELED, COMPLETED
    private String status;
    private LocalDateTime timestamp;
}
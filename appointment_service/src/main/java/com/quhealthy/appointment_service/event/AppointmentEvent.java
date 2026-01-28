package com.quhealthy.appointment_service.event;

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
    private String eventType; // APPOINTMENT_CREATED, COMPLETED, CANCELED
    private String status;
    private LocalDateTime timestamp;
}
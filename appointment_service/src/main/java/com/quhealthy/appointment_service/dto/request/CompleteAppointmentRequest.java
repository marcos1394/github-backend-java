package com.quhealthy.appointment_service.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CompleteAppointmentRequest {

    @Size(max = 2000, message = "Las notas privadas son muy largas")
    private String privateNotes; // Historial clínico breve

    private boolean generateReviewRequest = true; // Si queremos disparar el email de reseña
}
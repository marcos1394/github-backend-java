package com.quhealthy.appointment_service.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.quhealthy.appointment_service.model.enums.AppointmentType;
import com.quhealthy.appointment_service.model.enums.PaymentMethod;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CreateAppointmentRequest {

    @NotNull(message = "El ID del proveedor es obligatorio")
    private Long providerId;

    @NotNull(message = "El ID del servicio es obligatorio")
    private Long serviceId;

    @NotNull(message = "La fecha de inicio es obligatoria")
    @Future(message = "La cita debe ser en el futuro") // 👈 Validación Enterprise
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") // Formato estándar ISO
    private LocalDateTime startTime;

    // Nota: No pedimos 'endTime', el backend lo calcula según la duración del servicio en Catalog

    @NotNull(message = "El tipo de cita es obligatorio (IN_PERSON / ONLINE)")
    private AppointmentType appointmentType;

    @NotNull(message = "El método de pago es obligatorio")
    private PaymentMethod paymentMethod;

    @Size(max = 500, message = "Los síntomas no pueden exceder 500 caracteres")
    private String patientSymptoms;
}
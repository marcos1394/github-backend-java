package com.quhealthy.appointment_service.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.quhealthy.appointment_service.model.enums.AppointmentStatus;
import com.quhealthy.appointment_service.model.enums.AppointmentType;
import com.quhealthy.appointment_service.model.enums.PaymentMethod;
import com.quhealthy.appointment_service.model.enums.PaymentStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class AppointmentResponse {

    private Long id;
    
    // Info básica de actores
    private Long providerId;
    private Long consumerId;

    // Snapshot del Servicio
    private Long serviceId;
    private String serviceName;
    private BigDecimal price;
    private String currency;

    // Tiempos
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    // Estados
    private AppointmentStatus status;
    private AppointmentType type;
    
    // Detalles de modalidad
    private String meetLink;        // Solo si es ONLINE
    private String locationAddress; // Solo si es IN_PERSON

    // Finanzas
    private PaymentStatus paymentStatus;
    private PaymentMethod paymentMethod;
    private BigDecimal amountPaid;

    // Notas (Solo si el usuario tiene permiso de verlas, el mapper lo decidirá)
    private String patientSymptoms;
}
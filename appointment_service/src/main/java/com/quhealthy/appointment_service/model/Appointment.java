package com.quhealthy.appointment_service.model;

import com.quhealthy.appointment_service.model.enums.AppointmentStatus;
import com.quhealthy.appointment_service.model.enums.AppointmentType;
import com.quhealthy.appointment_service.model.enums.PaymentMethod;
import com.quhealthy.appointment_service.model.enums.PaymentStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "appointments", indexes = {
    @Index(name = "idx_appt_provider", columnList = "provider_id"),
    @Index(name = "idx_appt_consumer", columnList = "consumer_id"),
    @Index(name = "idx_appt_dates", columnList = "start_time, end_time"),
    @Index(name = "idx_appt_status", columnList = "status")
})
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // =================================================================
    // 👥 ACTORES
    // =================================================================
    @NotNull
    @Column(name = "provider_id", nullable = false)
    private Long providerId;

    @NotNull
    @Column(name = "consumer_id", nullable = false)
    private Long consumerId;

    // =================================================================
    // 🏥 SERVICIO & SNAPSHOTS (Inmutabilidad Histórica)
    // =================================================================
    @NotNull
    @Column(name = "service_id", nullable = false)
    private Long serviceId;

    @Column(name = "service_name_snapshot", nullable = false)
    private String serviceNameSnapshot; 

    // =================================================================
    // 📅 AGENDA & MODALIDAD
    // =================================================================
    @NotNull
    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @NotNull
    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "appointment_type", nullable = false)
    private AppointmentType appointmentType; // IN_PERSON, ONLINE

    // --- Detalles Específicos de la Modalidad ---
    
    @Column(name = "meet_link")
    private String meetLink; // Para ONLINE (Generado por Google/Zoom)

    @Column(name = "location_address")
    private String locationAddress; // Para IN_PERSON (Snapshot de la dirección del consultorio)

    // =================================================================
    // 🚦 ESTADO
    // =================================================================
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AppointmentStatus status;

    @Column(name = "cancellation_reason")
    private String cancellationReason; // Vital para analytics ("Por qué cancelan mis pacientes?")

    // =================================================================
    // 💰 FINANZAS (Soporte Híbrido: Online + Offline)
    // =================================================================
    
    @Column(name = "total_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalPrice; // Cuánto cuesta el servicio

    @Column(name = "amount_paid", precision = 10, scale = 2)
    private BigDecimal amountPaid; // Cuánto ha pagado realmente (puede ser 0 si es CASH PENDING)

    @Column(name = "currency", length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false)
    private PaymentStatus paymentStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method")
    private PaymentMethod paymentMethod;

    // Si usó seguro, guardamos referencia
    @Column(name = "insurance_policy_number")
    private String insurancePolicyNumber;

    // Si usó paquete, referenciamos el saldo consumido
    @Column(name = "consumer_package_balance_id")
    private Long consumerPackageBalanceId;

    // =================================================================
    // 📝 NOTAS
    // =================================================================
    @Column(name = "private_notes", columnDefinition = "TEXT")
    private String privateNotes; // Historial clínico breve / notas del doctor

    @Column(name = "patient_symptoms", columnDefinition = "TEXT")
    private String patientSymptoms; // "Motivo de consulta" ingresado al reservar

    // =================================================================
    // 🕒 AUDITORÍA
    // =================================================================
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
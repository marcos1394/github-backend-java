package com.quhealthy.catalog_service.model;

import com.quhealthy.catalog_service.model.enums.Currency;
import com.quhealthy.catalog_service.model.enums.ServiceStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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
@Table(name = "medical_services", indexes = {
    @Index(name = "idx_service_provider", columnList = "provider_id"),
    @Index(name = "idx_service_status", columnList = "status")
})
public class MedicalService {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "provider_id", nullable = false)
    private Long providerId; // El Doctor dueño de este servicio

    @NotBlank(message = "El nombre del servicio es obligatorio")
    @Column(nullable = false, length = 150)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    // --- Precios (Enterprise: Usar BigDecimal) ---
    
    @NotNull(message = "El precio es obligatorio")
    @DecimalMin(value = "0.0", inclusive = false, message = "El precio debe ser mayor a 0")
    @Column(precision = 10, scale = 2, nullable = false) 
    private BigDecimal price;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(length = 3)
    private Currency currency = Currency.MXN; // Default a MXN

    // --- Duración y Logística ---

    @NotNull(message = "La duración es obligatoria")
    @Min(value = 5, message = "La duración mínima son 5 minutos")
    @Column(name = "duration_minutes")
    private Integer durationMinutes; // Para agendar en el calendario

    @NotNull
    @Enumerated(EnumType.STRING)
    private ServiceStatus status = ServiceStatus.ACTIVE;

    // --- Auditoría ---

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
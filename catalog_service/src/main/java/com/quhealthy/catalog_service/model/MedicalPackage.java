package com.quhealthy.catalog_service.model;

import com.quhealthy.catalog_service.model.enums.Currency;
import com.quhealthy.catalog_service.model.enums.ServiceStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
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
import java.util.HashSet;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "medical_packages", indexes = {
    @Index(name = "idx_package_provider", columnList = "provider_id")
})
public class MedicalPackage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "provider_id", nullable = false)
    private Long providerId;

    @NotBlank
    @Column(nullable = false, length = 150)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    // --- Precio del Paquete (Suele tener descuento) ---
    
    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    @Column(precision = 10, scale = 2, nullable = false)
    private BigDecimal price; // El precio final que paga el paciente

    @NotNull
    @Enumerated(EnumType.STRING)
    private Currency currency = Currency.MXN;

    @NotNull
    @Enumerated(EnumType.STRING)
    private ServiceStatus status = ServiceStatus.ACTIVE;

    // --- Relación: Un paquete contiene muchos servicios ---
    
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "package_services_link",
        joinColumns = @JoinColumn(name = "package_id"),
        inverseJoinColumns = @JoinColumn(name = "service_id")
    )
    private Set<MedicalService> services = new HashSet<>();

    // --- Auditoría ---

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
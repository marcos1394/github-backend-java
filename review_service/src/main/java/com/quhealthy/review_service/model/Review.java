package com.quhealthy.review_service.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "reviews", indexes = {
    @Index(name = "idx_review_provider", columnList = "provider_id"), // Optimiza la carga del perfil del doctor
    @Index(name = "idx_review_consumer", columnList = "consumer_id")  // Optimiza "Mis reseñas"
})
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // --- Relaciones Lógicas (Microservicios) ---
    // No usamos @ManyToOne hacia User porque User está en otro microservicio.
    // Guardamos solo el ID.

    @NotNull
    @Column(name = "consumer_id", nullable = false)
    private Long consumerId; // El paciente que escribe

    @NotNull
    @Column(name = "provider_id", nullable = false)
    private Long providerId; // El doctor que recibe

    @Column(name = "service_id")
    private Long serviceId;  // (Opcional) ID del servicio específico (ej: "Consulta General")

    // --- Datos de la Reseña ---

    @NotNull
    @Min(value = 1, message = "La calificación mínima es 1 estrella")
    @Max(value = 5, message = "La calificación máxima son 5 estrellas")
    @Column(nullable = false)
    private Integer rating;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column(name = "is_verified")
    private boolean isVerified = false; // True si el sistema confirmó que hubo una cita pagada

    // --- Respuesta del Proveedor (Gestión de Reputación) ---
    
    @Column(name = "provider_response", columnDefinition = "TEXT")
    private String providerResponse;

    @Column(name = "response_at")
    private LocalDateTime responseAt;

    // --- Auditoría ---

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
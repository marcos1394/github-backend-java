package com.quhealthy.appointment_service.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "consumer_package_balances", indexes = {
    @Index(name = "idx_cpb_consumer", columnList = "consumer_id"),
    @Index(name = "idx_cpb_provider", columnList = "provider_id")
})
public class ConsumerPackageBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "consumer_id", nullable = false)
    private Long consumerId;

    @Column(name = "provider_id", nullable = false)
    private Long providerId;

    @Column(name = "service_id", nullable = false)
    private Long serviceId; // El servicio específico que puede canjear

    @Column(name = "package_id_snapshot", nullable = false)
    private Long packageIdSnapshot; // Referencia al catálogo

    @Column(name = "remaining_credits", nullable = false)
    private Integer remainingCredits; // Ej: Le quedan 3 consultas

    @Column(name = "expiration_date")
    private LocalDateTime expirationDate;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
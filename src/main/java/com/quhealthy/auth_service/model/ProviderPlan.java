package com.quhealthy.auth_service.model;

import com.quhealthy.auth_service.model.enums.PlanStatus;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "provider_plans") // Sequelize usaba "ProviderPlans", en Java estándar usamos snake_case minúscula en BD
public class ProviderPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Relación Many-to-One con Provider (Lazy para performance)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id", nullable = false)
    private Provider provider;

    // Relación Many-to-One con Plan
    @ManyToOne(fetch = FetchType.EAGER) // Eager porque casi siempre queremos saber los detalles del plan
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlanStatus status;

    @Column(nullable = false)
    private LocalDateTime startDate;

    @Column(nullable = false)
    private LocalDateTime endDate;

    // --- Datos de Pasarela ---
    @Column(name = "payment_gateway")
    private String paymentGateway; // "STRIPE", "PAYPAL"

    @Column(name = "transaction_id")
    private String transactionId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
package com.quhealthy.auth_service.model;

import com.quhealthy.auth_service.model.enums.PlanStatus;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "provider_plans")
public class ProviderPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // --- RELACIONES ---

    // Many-to-One con Provider
    // IMPORTANTE: Agregamos @ToString.Exclude para evitar bucles infinitos y errores de memoria
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Provider provider;

    // Many-to-One con Plan
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    // --- ESTADO Y VIGENCIA ---

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlanStatus status;

    @Column(nullable = false)
    private LocalDateTime startDate;

    @Column(nullable = false)
    private LocalDateTime endDate;

    // Campo vital para saber cuándo cobrar la renovación
    @Column(name = "next_billing_date")
    private LocalDateTime nextBillingDate;

    // --- INTEGRACIÓN CON PASARELA (STRIPE) ---
    // Estos son los campos que faltaban y causaban el error en el Pipeline

    @Column(name = "stripe_subscription_id", unique = true)
    private String stripeSubscriptionId; // <--- EL CAMPO QUE RECLAMABA EL ERROR

    @Column(name = "stripe_customer_id")
    private String stripeCustomerId;

    @Column(name = "payment_gateway")
    private String paymentGateway; // "STRIPE", "PAYPAL"

    @Column(name = "transaction_id")
    private String transactionId; // ID del último pago exitoso

    @Column(name = "is_auto_renew")
    private boolean isAutoRenew = true;

    // --- AUDITORÍA ---

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
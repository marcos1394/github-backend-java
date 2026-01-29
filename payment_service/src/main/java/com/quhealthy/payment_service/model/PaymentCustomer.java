package com.quhealthy.payment_service.model;

import jakarta.persistence.*;
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
@Table(name = "payment_customers", indexes = {
    // 🚀 Búsqueda rápida: "Trae el perfil de pagos del paciente X"
    @Index(name = "idx_payment_customer_user", columnList = "user_id") 
})
public class PaymentCustomer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ID del Consumidor (Consumer) que viene del Auth Service
    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    // ID del Cliente en Stripe (ej: "cus_NffrH...")
    // Aquí es donde Stripe guarda las tarjetas de crédito de forma segura.
    @Column(name = "stripe_customer_id", nullable = false, unique = true)
    private String stripeCustomerId;

    // Opcional: Guardamos cuál es su tarjeta predeterminada para agilizar el UI
    @Column(name = "default_payment_method_id")
    private String defaultPaymentMethodId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
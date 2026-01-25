package com.quhealthy.payment_service.model;

import com.quhealthy.payment_service.model.enums.PaymentGateway;
import com.quhealthy.payment_service.model.enums.SubscriptionStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "subscriptions", indexes = {
        @Index(name = "idx_provider_status", columnList = "provider_id, status"), // Búsqueda rápida: ¿El usuario X está activo?
        @Index(name = "idx_external_sub_id", columnList = "external_subscription_id") // Búsqueda rápida para Webhooks
})
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "provider_id", nullable = false)
    private Long providerId; // ID del Doctor/Nutriólogo (Viene del Auth Service)

    // --- DETALLES DEL PLAN ---
    @Column(name = "plan_id", nullable = false)
    private String planId; // Ej: "price_1HhD...", "PLAN_PRO_YEARLY"

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentGateway gateway; // STRIPE o MERCADOPAGO

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionStatus status;

    // --- REFERENCIAS EXTERNAS (CRÍTICO PARA WEBHOOKS) ---
    
    // El ID de la suscripción en Stripe (sub_...) o MP (preapproval_...)
    @Column(name = "external_subscription_id", unique = true)
    private String externalSubscriptionId;

    // El ID del cliente en Stripe (cus_...) o MP (payer_id)
    // Sirve para no pedir tarjeta de nuevo en el futuro
    @Column(name = "external_customer_id")
    private String externalCustomerId;

    // --- FECHAS DE VIGENCIA ---
    
    @Column(name = "current_period_start")
    private LocalDateTime currentPeriodStart;

    @Column(name = "current_period_end")
    private LocalDateTime currentPeriodEnd; // Aquí sabremos cuándo cortar el servicio

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt; // Si el usuario canceló, guardamos cuándo lo hizo

    // --- METADATA ---
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    // Helper para saber si está válida sin importar el estado exacto (ej: CANCELED pero aún en periodo pagado)
    public boolean isValid() {
        if (status == SubscriptionStatus.ACTIVE || status == SubscriptionStatus.TRIALING) {
            return true;
        }
        // Si canceló, pero la fecha de fin aún no llega (Cancel at period end)
        if (status == SubscriptionStatus.CANCELED && currentPeriodEnd != null) {
            return currentPeriodEnd.isAfter(LocalDateTime.now());
        }
        return false;
    }
}
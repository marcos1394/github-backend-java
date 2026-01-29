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
@Table(name = "merchant_accounts", indexes = {
    // 🚀 INDEX: Vital para buscar rápido "Dáme la cuenta Stripe del Doctor ID 500"
    @Index(name = "idx_merchant_user", columnList = "user_id") 
})
public class MerchantAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Vinculación lógica con el Auth Service (Provider.java)
    // Usamos Long porque es el estándar que infiero de tus modelos
    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId; 

    // El ID de la cuenta conectada en Stripe (ej: "acct_1MeDj2...")
    @Column(name = "stripe_account_id", nullable = false, unique = true)
    private String stripeAccountId;

    // --- Estados de Compliance (Stripe KYC) ---
    
    // ¿El usuario completó el formulario de Stripe?
    @Column(name = "details_submitted")
    private boolean detailsSubmitted = false;

    // ¿Stripe ya nos deja procesar tarjetas para este doctor?
    @Column(name = "charges_enabled")
    private boolean chargesEnabled = false;

    // ¿Stripe ya nos deja enviarle el dinero a su cuenta bancaria?
    @Column(name = "payouts_enabled")
    private boolean payoutsEnabled = false;
    
    // Configuración Regional
    @Column(name = "country", length = 2)
    private String country; // "MX"
    
    @Column(name = "default_currency", length = 3)
    private String defaultCurrency; // "mxn"

    // --- Auditoría ---

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
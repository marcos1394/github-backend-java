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
@Table(name = "merchant_accounts")
public class MerchantAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "stripe_account_id", nullable = false, unique = true)
    private String stripeAccountId;

    // ✅ AGREGAMOS ESTE CAMPO QUE FALTABA
    @Column(name = "onboarding_completed")
    private boolean onboardingCompleted = false;

    @Column(name = "payouts_enabled")
    private boolean payoutsEnabled = false;

    @Column(name = "charges_enabled")
    private boolean chargesEnabled = false;

    @Column(name = "country", length = 2)
    private String country;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
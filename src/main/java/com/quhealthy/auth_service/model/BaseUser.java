package com.quhealthy.auth_service.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@MappedSuperclass // IMPORTANTE: Esto dice "No hagas tabla de esto, pero hereda mis campos"
public abstract class BaseUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    // --- Verificación de Email ---
    @Column(name = "is_email_verified")
    private boolean isEmailVerified = false;

    @Column(name = "email_verification_token")
    private String emailVerificationToken;

    @Column(name = "email_verification_expires")
    private LocalDateTime emailVerificationExpires;

    // --- Verificación de Teléfono ---
    private String phone; // Movidó aquí porque ambos lo tienen, aunque en consumer era phoneNumber

    @Column(name = "is_phone_verified")
    private boolean isPhoneVerified = false;

    @Column(name = "phone_verification_token")
    private String phoneVerificationToken;

    @Column(name = "phone_verification_expires")
    private LocalDateTime phoneVerificationExpires;

    // --- Seguridad 2FA ---
    @Column(name = "two_factor_secret", columnDefinition = "TEXT")
    private String twoFactorSecret;

    @Column(name = "is_two_factor_enabled")
    private boolean isTwoFactorEnabled = false;

    // --- Timestamps Automáticos ---
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
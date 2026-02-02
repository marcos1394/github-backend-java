package com.quhealthy.auth_service.model;

import com.quhealthy.auth_service.model.enums.UserStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.security.core.userdetails.UserDetails; // Importante

import java.time.LocalDateTime;

/**
 * Clase abstracta que contiene los datos comunes de todos los usuarios.
 * Tanto Consumer como Provider heredan de esta clase.
 *
 * RESPONSABILIDADES:
 * - Identidad base (firstName, lastName, email, password)
 * - Verificación de email y teléfono
 * - Autenticación y recuperación de contraseña
 * - Seguridad 2FA
 * - Auditoría (timestamps, status, soft delete)
 * - Tracking (lastLoginAt)
 *
 * NO INCLUYE:
 * - Datos específicos de Consumer (birthDate, bio, etc)
 * - Datos específicos de Provider (businessName, category, etc)
 * - Datos de onboarding (licencia, KYC, marketplace)
 * - Datos de integraciones (Google Calendar, Stripe)
 * - Datos de referidos
 * - Datos de notificaciones
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@MappedSuperclass
public abstract class BaseUser implements UserDetails {

    // ========================================================================
    // 🔑 IDENTIDAD PRIMARY
    // ========================================================================

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Nombre del usuario (persona física).
     * Para empresas, se usa businessName en Provider.
     */
    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    /**
     * Apellido del usuario (persona física).
     */
    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    /**
     * Email único del usuario.
     * Utilizado para login y comunicaciones.
     */
    @Column(nullable = false, unique = true, length = 150)
    private String email;

    /**
     * Contraseña hasheada (bcrypt).
     * NUNCA almacenar en texto plano.
     */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String password;

    /**
     * Teléfono del usuario (internacional).
     * Formato: +1-234-567-8900 o similar.
     */
    @Column(name = "phone", length = 20)
    private String phone;

    // ========================================================================
    // ✉️ VERIFICACIÓN DE EMAIL
    // ========================================================================

    /**
     * Flag que indica si el email ha sido verificado.
     * Requerido para algunos endpoints (ej: crear citas).
     */
    @Column(name = "is_email_verified", nullable = false)
    public boolean isEmailVerified = false;

    /**
     * Token JWT para verificación de email.
     * Generado al registro, validado en email de confirmación.
     */
    @Column(name = "email_verification_token", length = 500)
    private String emailVerificationToken;

    /**
     * Fecha de expiración del token de verificación de email.
     * Típicamente 24 horas después del registro.
     */
    @Column(name = "email_verification_expires")
    private LocalDateTime emailVerificationExpires;

    // ========================================================================
    // 📱 VERIFICACIÓN DE TELÉFONO
    // ========================================================================

    /**
     * Flag que indica si el teléfono ha sido verificado.
     * Requerido para 2FA.
     */
    @Column(name = "is_phone_verified", nullable = false)
    public boolean isPhoneVerified = false;

    /**
     * Token OTP para verificación de teléfono.
     * Enviado por SMS, validado en el app.
     */
    @Column(name = "phone_verification_token", length = 10)
    private String phoneVerificationToken;



    /**
     * Fecha de expiración del token OTP de teléfono.
     * Típicamente 10 minutos.
     */
    @Column(name = "phone_verification_expires")
    private LocalDateTime phoneVerificationExpires;

    // ========================================================================
    // 🔐 RECUPERACIÓN DE CONTRASEÑA (Selector/Verifier Pattern)
    // ========================================================================

    /**
     * Selector para búsqueda rápida (64 caracteres hexadecimales).
     * Se envía al usuario por email junto con el verifier.
     * Permite búsqueda en BD sin exponer el verifier.
     */
    @Column(name = "reset_selector", length = 128)
    private String resetSelector;

    /**
     * Hash del verifier (bcrypt).
     * Se compara con el verifier enviado por el usuario.
     */
    @Column(name = "reset_verifier_hash", columnDefinition = "TEXT")
    private String resetVerifierHash;

    /**
     * Fecha de expiración del token de reset.
     * Típicamente 1 hora.
     */
    @Column(name = "reset_token_expires_at")
    private LocalDateTime resetTokenExpiresAt;

    // ========================================================================
    // 🛡️ SEGURIDAD 2FA (Two-Factor Authentication)
    // ========================================================================

    /**
     * Secreto TOTP para autenticación de dos factores.
     * Base32 encoded, generado con Google Authenticator.
     */
    @Column(name = "two_factor_secret", columnDefinition = "TEXT")
    private String twoFactorSecret;

    /**
     * Flag que indica si 2FA está habilitado.
     * El usuario debe verificar ambos: email Y 2FA para login.
     */
    @Column(name = "is_two_factor_enabled", nullable = false)
    private Boolean isTwoFactorEnabled = false;

    // ========================================================================
    // 📊 ESTADO Y STATUS
    // ========================================================================

    /**
     * Estado actual del usuario.
     * ACTIVE: puede usar la plataforma
     * INACTIVE: registrado pero sin verificar email
     * SUSPENDED: violó términos de servicio
     * DEACTIVATED: usuario desactivó voluntariamente
     * DELETED: soft delete (GDPR)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    /**
     * Fecha de soft delete (GDPR compliance).
     * NULL = usuario activo
     * Fecha = usuario ha sido "eliminado"
     */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // ========================================================================
    // 📅 TRACKING Y AUDITORÍA
    // ========================================================================

    /**
     * Última fecha/hora de login exitoso.
     * Útil para analytics, seguridad, y estadísticas.
     */
    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    /**
     * Timestamp de creación del usuario.
     * Inmutable (no se actualiza nunca).
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Timestamp de última actualización.
     * Se actualiza cada vez que el usuario modifica sus datos.
     */
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ========================================================================
    // 🔧 MÉTODOS HELPER
    // ========================================================================

    /**
     * Retorna el nombre completo del usuario.
     * @return "firstName lastName"
     */
    public String getFullName() {
        return firstName + " " + lastName;
    }

    /**
     * Verifica si el usuario ha sido eliminado (soft delete).
     * @return true si deletedAt != null
     */
    public boolean isDeleted() {
        return deletedAt != null;
    }

    /**
     * Verifica si el usuario está activo y puede usar la plataforma.
     * Condiciones:
     * 1. Status es ACTIVE
     * 2. No ha sido eliminado (deletedAt == null)
     * @return true si puede usar la plataforma
     */
    public boolean isActive() {
        return status == UserStatus.ACTIVE && deletedAt == null;
    }

    /**
     * Verifica si el usuario puede hacer login.
     * Condiciones:
     * 1. Debe estar activo
     * 2. Email debe estar verificado
     * @return true si puede hacer login
     */
    public boolean canLogin() {
        return isActive() && isEmailVerified;
    }

    /**
     * Verifica si el usuario tiene token de reset válido.
     * @return true si el token no ha expirado
     */
    public boolean hasValidResetToken() {
        return resetTokenExpiresAt != null &&
                resetTokenExpiresAt.isAfter(LocalDateTime.now());
    }

    /**
     * Verifica si el usuario tiene email verification válido.
     * @return true si el token no ha expirado
     */
    public boolean hasValidEmailVerificationToken() {
        return emailVerificationExpires != null &&
                emailVerificationExpires.isAfter(LocalDateTime.now());
    }

    /**
     * Verifica si el usuario tiene phone verification válido.
     * @return true si el token no ha expirado
     */
    public boolean hasValidPhoneVerificationToken() {
        return phoneVerificationExpires != null &&
                phoneVerificationExpires.isAfter(LocalDateTime.now());
    }
}
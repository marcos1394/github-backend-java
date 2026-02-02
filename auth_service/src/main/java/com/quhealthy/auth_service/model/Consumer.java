package com.quhealthy.auth_service.model;

import com.quhealthy.auth_service.model.enums.Gender;
import com.quhealthy.auth_service.model.enums.Role;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder; // IMPORTANTE
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/**
 * Entidad Consumer - Pacientes/Consumidores de servicios de salud y belleza.
 *
 * RESPONSABILIDADES:
 * - Perfil de paciente (birthDate, gender, bio)
 * - Preferencias personales (language, timezone)
 * - Preferencias de notificación (email, SMS, marketing)
 * - UserDetails para Spring Security
 *
 * HEREDA DE BaseUser:
 * - firstName, lastName, email, password, phone
 * - Email/Phone verification
 * - 2FA
 * - Password reset tokens
 * - Status y timestamps
 *
 * ESTRATEGIA BD (Neon):
 * Tabla centralizada en esquema único por ahora para optimización de costos.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "consumers", indexes = {
        @Index(name = "idx_consumers_email", columnList = "email"),
        @Index(name = "idx_consumers_status", columnList = "status"),
        @Index(name = "idx_consumers_created_at", columnList = "created_at"),
        @Index(name = "idx_consumers_deleted_at", columnList = "deleted_at"),
        @Index(name = "idx_consumers_gender", columnList = "gender")
})
public class Consumer extends BaseUser implements UserDetails {

    // ========================================================================
    // 👤 ROL
    // ========================================================================

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role = Role.CONSUMER;

    // ========================================================================
    // 🖼️ PERFIL
    // ========================================================================

    @Column(name = "profile_image_url", columnDefinition = "TEXT")
    private String profileImageUrl;

    @Column(name = "bio", columnDefinition = "TEXT", length = 500)
    private String bio;

    // ========================================================================
    // 👶 DATOS PERSONALES
    // ========================================================================

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 20)
    private Gender gender;

    // ========================================================================
    // 🌍 PREFERENCIAS
    // ========================================================================

    @Column(name = "preferred_language", length = 5)
    private String preferredLanguage = "es";

    @Column(name = "timezone", length = 50)
    private String timezone = "America/Bogota";

    // ========================================================================
    // 🔔 PREFERENCIAS DE NOTIFICACIÓN
    // ========================================================================

    @Column(name = "email_notifications_enabled", nullable = false)
    private Boolean emailNotificationsEnabled = true;

    @Column(name = "sms_notifications_enabled", nullable = false)
    private Boolean smsNotificationsEnabled = false;

    @Column(name = "marketing_emails_opt_in", nullable = false)
    private Boolean marketingEmailsOptIn = false;

    @Column(name = "appointment_reminders_enabled", nullable = false)
    private Boolean appointmentRemindersEnabled = true;

    // ========================================================================
    // ⚖️ LEGAL (Nuevo para Auth)
    // ========================================================================

    /**
     * Indica si el usuario aceptó explícitamente los Términos y Condiciones.
     * Requerido legalmente para operar la plataforma.
     */
    @Column(name = "terms_accepted", nullable = false)
    private boolean termsAccepted;

    // ========================================================================
    // 🔐 IMPLEMENTACIÓN DE UserDetails (Spring Security)
    // ========================================================================

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getUsername() {
        return getEmail();
    }

    @Override
    public String getPassword() {
        return super.getPassword();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return isActive();
    }
}
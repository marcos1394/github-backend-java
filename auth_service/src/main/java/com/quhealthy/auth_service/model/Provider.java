package com.quhealthy.auth_service.model;

import com.quhealthy.auth_service.model.enums.Archetype;
import com.quhealthy.auth_service.model.enums.PlanStatus;
import com.quhealthy.auth_service.model.enums.Role;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.locationtech.jts.geom.Point; // Requiere hibernate-spatial

// --- Imports de Spring Security (NUEVOS) ---
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "providers")
// ✅ CAMBIO CRÍTICO: Implementar UserDetails
public class Provider extends BaseUser implements UserDetails {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.PROVIDER;

    @Column(name = "accept_terms", nullable = false)
    private boolean acceptTerms;

    // --- Datos de Negocio ---
    @Column(name = "business_name")
    private String businessName;

    private String address;

    // Coordenadas simples
    private Double lat;
    private Double lng;

    // Coordenadas Espaciales (PostGIS)
    @Column(columnDefinition = "geography(Point,4326)")
    private Point location;

    // --- Categorización ---
    @Column(name = "parent_category_id", nullable = false)
    private Long parentCategoryId;

    @Column(name = "category_provider_id")
    private Long categoryProviderId;

    @Column(name = "sub_category_id")
    private Long subCategoryId;

    // --- Estado y Onboarding ---
    @Enumerated(EnumType.STRING)
    private Archetype archetype;

    // ✅ CAMBIO: Renombrado a isKycVerified (CamelCase estándar) para que Lombok genere isKycVerified()
    @Column(name = "is_kyc_verified")
    private boolean isKycVerified = false;

    @Column(name = "is_license_verified")
    private boolean isLicenseVerified = false;

    @Column(name = "is_marketplace_configured")
    private boolean isMarketplaceConfigured = false;

    @Column(name = "onboarding_complete")
    private boolean onboardingComplete = false;

    // --- Plan & Suscripción ---
    @Enumerated(EnumType.STRING)
    @Column(name = "plan_status", nullable = false)
    private PlanStatus planStatus = PlanStatus.TRIAL;

    @Column(name = "trial_expires_at")
    private LocalDateTime trialExpiresAt;

    // --- Integraciones ---
    @Column(name = "stripe_account_id", unique = true)
    private String stripeAccountId;

    @Column(name = "google_access_token", columnDefinition = "TEXT")
    private String googleAccessToken;

    @Column(name = "google_refresh_token", columnDefinition = "TEXT")
    private String googleRefreshToken;

    @Column(name = "google_token_expiry")
    private LocalDateTime googleTokenExpiry;

    @Column(name = "google_calendar_id")
    private String googleCalendarId;

    // =================================================================
    // 🔐 RECUPERACIÓN DE CONTRASEÑA (Selector/Verifier Pattern)
    // =================================================================
    
    @Column(name = "reset_selector")
    private String resetSelector;

    @Column(name = "reset_verifier_hash")
    private String resetVerifierHash;

    @Column(name = "reset_token_expires_at")
    private LocalDateTime resetTokenExpiresAt;

    // --- Sistema de Referidos ---
    @Column(name = "referral_code", unique = true)
    private String referralCode;

    @Column(name = "referred_by_id")
    private Integer referredById;

    // =================================================================
    // 🛡️ 2FA (Agregado para completitud Enterprise)
    // =================================================================
    
    @Column(name = "is_two_factor_enabled")
    private Boolean isTwoFactorEnabled = false;

    @Column(name = "two_factor_secret", columnDefinition = "TEXT")
    private String twoFactorSecret; 

    // --- RELACIONES ---

    @OneToMany(mappedBy = "provider", fetch = FetchType.LAZY)
    private List<ProviderPlan> plans;

    @ManyToMany(cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
        name = "provider_tags",
        joinColumns = @JoinColumn(name = "provider_id"),
        inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    @ToString.Exclude 
    @EqualsAndHashCode.Exclude 
    private Set<Tag> tags = new HashSet<>();

    // =================================================================
    // 👮 IMPLEMENTACIÓN DE USER DETAILS (SPRING SECURITY)
    // =================================================================

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.name()));
    }

    @Override
    public String getUsername() {
        // Asumiendo que 'email' está en BaseUser
        return getEmail(); 
    }

    @Override
    public String getPassword() {
        // Asumiendo que 'password' está en BaseUser
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
        return true;
    }
}
package com.quhealthy.auth_service.model;

import com.quhealthy.auth_service.model.enums.Archetype;
import com.quhealthy.auth_service.model.enums.PlanStatus;
import com.quhealthy.auth_service.model.enums.Role;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.locationtech.jts.geom.Point; // Requiere hibernate-spatial

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "providers")
public class Provider extends BaseUser {

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

    // Coordenadas Espaciales (PostGIS) - Nivel PRO
    @Column(columnDefinition = "geography(Point,4326)")
    private Point location;

    // --- Categorización (IDs o Relaciones) ---
    // En microservicios puros, a veces guardamos solo el ID si la categoría vive en otro servicio.
    // Si es un monolito modular, usamos @ManyToOne. Asumiré IDs por ahora para migración directa.
    @Column(name = "parent_category_id", nullable = false)
    private Long parentCategoryId;

    @Column(name = "category_provider_id")
    private Long categoryProviderId;

    @Column(name = "sub_category_id")
    private Long subCategoryId;

    // --- Estado y Onboarding ---
    @Enumerated(EnumType.STRING)
    private Archetype archetype;

    @Column(name = "is_kyc_verified")
    private boolean isKYCVerified = false;

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

    // --- Integraciones (Stripe & Google) ---
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

    // --- Sistema de Referidos ---
    @Column(name = "referral_code", unique = true)
    private String referralCode;

    @Column(name = "referred_by_id")
    private Integer referredById;

    // --- Relaciones ---
    // Aquí irían tus @OneToMany para planes, kyc, reviews, etc.
    // Ejemplo:
    // @OneToMany(mappedBy = "provider")
    // private List<ProviderPlan> subscriptions;
}
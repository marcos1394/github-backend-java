package com.quhealthy.auth_service.model;

import com.quhealthy.auth_service.model.enums.Archetype;
import com.quhealthy.auth_service.model.enums.PlanStatus;
import com.quhealthy.auth_service.model.enums.Role;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.locationtech.jts.geom.Point; // Requiere hibernate-spatial
import java.util.List; // <--- IMPORTE NECESARIO PARA LA LISTA
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

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
    // Nota: Asegúrate de que la BD tenga la extensión PostGIS activada
    @Column(columnDefinition = "geography(Point,4326)")
    private Point location;

    // --- Categorización (IDs o Relaciones) ---
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

    // --- RELACIONES (LA SOLUCIÓN AL ERROR DEL PIPELINE) ---
    
    // =================================================================
    // ✅ NUEVOS CAMPOS (Corrección para el Login)
    // =================================================================
    
    // 1. Autenticación de Dos Factores (2FA)
    // Usamos Boolean (Wrapper) para que Lombok genere "getIsTwoFactorEnabled()"
    // Si usáramos boolean (primitivo), generaría "isIsTwoFactorEnabled()" o similar.
    @Column(name = "is_two_factor_enabled")
    private Boolean isTwoFactorEnabled = false;

    @OneToMany(mappedBy = "provider", fetch = FetchType.LAZY)
    private List<ProviderPlan> plans;


    // Esta es la variable que Hibernate estaba buscando y no encontraba.
    // El nombre 'tags' debe coincidir con mappedBy="tags" en la clase Tag.
    @ManyToMany(cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
        name = "provider_tags",
        joinColumns = @JoinColumn(name = "provider_id"),
        inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    @ToString.Exclude // IMPORTANTE: Previene bucles infinitos en logs
    @EqualsAndHashCode.Exclude // IMPORTANTE: Previene bucles infinitos en comparaciones
    private Set<Tag> tags = new HashSet<>();

}
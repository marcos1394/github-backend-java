package com.quhealthy.auth_service.model;

import com.quhealthy.auth_service.model.enums.Archetype;
import com.quhealthy.auth_service.model.enums.Gender;
import com.quhealthy.auth_service.model.enums.LegalEntityType;
import com.quhealthy.auth_service.model.enums.Role;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Entidad Provider - Profesionales de salud y belleza.
 *
 * RESPONSABILIDADES:
 * - Identidad profesional y categorización.
 * - Datos de ubicación y estado de onboarding.
 * - Relaciones JPA con catálogos (Categorías/Tags).
 *
 * FLUJO DE DATOS:
 * 1. Registro: Se llena businessName, email, password y parentCategoryId.
 * 2. Onboarding: Se llena category, subCategory, address, bio, etc.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
// CORRECCIÓN 1: Usamos solo callSuper, las exclusiones van en los campos
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "providers", indexes = {
        @Index(name = "idx_providers_email", columnList = "email"),
        @Index(name = "idx_providers_parent_category_id", columnList = "parent_category_id"),
        @Index(name = "idx_providers_category_id", columnList = "category_id"), // Index FK
        @Index(name = "idx_providers_status", columnList = "status"),
        @Index(name = "idx_providers_slug", columnList = "slug"),
        @Index(name = "idx_providers_onboarding_complete", columnList = "onboarding_complete")
})
public class Provider extends BaseUser implements UserDetails {

    // ========================================================================
    // 👤 ROL
    // ========================================================================

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    @Builder.Default
    private Role role = Role.PROVIDER;

    // ========================================================================
    // 🏢 IDENTIDAD PROFESIONAL / NEGOCIO
    // ========================================================================

    @Column(name = "business_name", nullable = false, length = 200)
    private String businessName;

    /**
     * Slug único para URL amigable.
     * Ej: "clinica-dental-sonrisas"
     * Se genera durante el onboarding o tras verificar el nombre.
     */
    @Column(name = "slug", unique = true)
    private String slug;

    @Column(name = "profile_image_url", columnDefinition = "TEXT")
    private String profileImageUrl;

    @Column(name = "bio", columnDefinition = "TEXT", length = 1000)
    private String bio;

    // ========================================================================
    // 📋 TIPO DE ENTIDAD LEGAL
    // ========================================================================

    @Enumerated(EnumType.STRING)
    @Column(name = "legal_entity_type", nullable = false, length = 20)
    @Builder.Default
    private LegalEntityType legalEntityType = LegalEntityType.PERSONA_FISICA;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 20)
    private Gender gender;

    // ========================================================================
    // 📍 UBICACIÓN
    // ========================================================================

    @Column(name = "address", length = 400)
    private String address;

    @Column(name = "latitude", precision = 10)
    private Double latitude;

    @Column(name = "longitude", precision = 11)
    private Double longitude;



    // ========================================================================
    // 🏷️ CATEGORIZACIÓN (JPA RELATIONS)
    // ========================================================================

    /**
     * ID de la industria (Salud vs Belleza).
     * Se captura en el REGISTRO.
     * Es un Long simple porque es el punto de partida del Wizard.
     */
    @Column(name = "parent_category_id", nullable = false)
    private Long parentCategoryId;

    /**
     * Categoría Específica (Dentista, Cardiólogo).
     * Se captura en el ONBOARDING.
     * Nullable = true (porque no existe al momento del registro).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = true)
    @ToString.Exclude
    // CORRECCIÓN 2: Exclusión explícita para evitar ciclos
    @EqualsAndHashCode.Exclude
    private CategoryProvider category;

    /**
     * Subcategoría (Ortodoncia).
     * Se captura en el ONBOARDING.
     * Nullable = true.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sub_category_id", nullable = true)
    @ToString.Exclude
    // CORRECCIÓN 3: Exclusión explícita para evitar ciclos
    @EqualsAndHashCode.Exclude
    private SubCategory subCategory;

    @Enumerated(EnumType.STRING)
    @Column(name = "archetype", length = 50)
    private Archetype archetype;

    // ========================================================================
    // 🚀 ESTADO Y LEGAL
    // ========================================================================

    @Builder.Default
    @Column(name = "onboarding_complete", nullable = false)
    private Boolean onboardingComplete = false;

    // NOTA: Se eliminó 'isEmailVerified' porque ya se hereda de BaseUser.
    // Redefinirlo aquí causa errores de "Repeated column" en Hibernate.

    /**
     * Paso actual del wizard.
     * Ej: "SPECIALTY_SELECTION", "DOCUMENTS_UPLOAD".
     */
    @Column(name = "current_onboarding_step")
    private String currentOnboardingStep;

    /**
     * Aceptación de Términos y Condiciones.
     * Requerido legalmente desde el registro.
     */
    @Column(name = "terms_accepted", nullable = false)
    private boolean termsAccepted;

    @Builder.Default
    @Column(name = "has_active_plan", nullable = false)
    private boolean hasActivePlan = false;

    // ========================================================================
    // 💳 REFERENCIA A PLAN
    // ========================================================================

    @Column(name = "current_plan_id")
    private Long currentPlanId;

    // ========================================================================
    // 🏷️ ETIQUETAS
    // ========================================================================

    @ManyToMany(
            cascade = {CascadeType.PERSIST, CascadeType.MERGE},
            fetch = FetchType.LAZY
    )
    @JoinTable(
            name = "provider_tags",
            joinColumns = @JoinColumn(name = "provider_id", nullable = false),
            inverseJoinColumns = @JoinColumn(name = "tag_id", nullable = false),
            indexes = {
                    @Index(name = "idx_provider_tags_provider_id", columnList = "provider_id"),
                    @Index(name = "idx_provider_tags_tag_id", columnList = "tag_id")
            }
    )
    @ToString.Exclude
    // CORRECCIÓN 4: Exclusión explícita para evitar ciclos en Set
    @EqualsAndHashCode.Exclude
    @Builder.Default
    private Set<Tag> tags = new HashSet<>();

    // ========================================================================
    // 🔐 IMPLEMENTACIÓN DE UserDetails
    // ========================================================================

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getUsername() {
        return getEmail();
    }

    @Override public String getPassword() { return super.getPassword(); }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return isActive(); }

    // ========================================================================
    // 🔧 MÉTODOS HELPER
    // ========================================================================

    public boolean isPersonaFisica() {
        return legalEntityType == LegalEntityType.PERSONA_FISICA;
    }

    public boolean isEmpresa() {
        return legalEntityType == LegalEntityType.EMPRESA;
    }

    public boolean hasLocation() {
        return latitude != null && longitude != null;
    }

    public boolean canOfferServices() {
        // isEmailVerified() viene de BaseUser
        return onboardingComplete && isEmailVerified() && isActive();
    }
}
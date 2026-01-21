package com.quhealthy.onboarding_service.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import com.quhealthy.onboarding_service.model.enums.Archetype;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "providers") // Mapea a la misma tabla física
public class Provider {

    // -------------------------------------------------------------------
    // 🔑 IDENTIFICACIÓN (Sin @GeneratedValue)
    // -------------------------------------------------------------------
    @Id
    @Column(name = "id")
    private Long id; // El ID viene del Token JWT, no lo generamos aquí.

    // -------------------------------------------------------------------
    // 👤 DATOS BÁSICOS (Traídos de lo que era BaseUser)
    // -------------------------------------------------------------------
    @Column(name = "name")
    private String name; // El nombre sí es editable en el perfil

    @Column(name = "email", insertable = false, updatable = false)
    private String email; // Solo lectura para referencia visual (seguridad)

    @Column(name = "phone")
    private String phone; // Útil para validación de contacto

    // -------------------------------------------------------------------
    // 🏢 DATOS DE NEGOCIO (Onboarding Core)
    // -------------------------------------------------------------------
    @Column(name = "business_name")
    private String businessName;

    @Column(name = "address")
    private String address;

    // Usamos lat/lng simples para evitar dependencia de Hibernate Spatial
    // si no vamos a hacer cálculos geométricos complejos en este microservicio.
    @Column(name = "lat")
    private Double lat;

    @Column(name = "lng")
    private Double lng;
    
    // Si necesitas 'location' (Point), necesitarías agregar la dependencia 'hibernate-spatial'
    // en el pom.xml de este servicio también. Por ahora lo omito para simplicidad.

    // -------------------------------------------------------------------
    // 🏷️ CATEGORIZACIÓN
    // -------------------------------------------------------------------
    @Column(name = "parent_category_id")
    private Long parentCategoryId;

    @Column(name = "category_provider_id")
    private Long categoryProviderId;

    @Column(name = "sub_category_id")
    private Long subCategoryId;

    // Nota: Si usas Enums (Archetype), debes copiar el archivo Enum a este proyecto
    // o mapearlo como String si quieres desacoplar totalmente.
    // Recomendación: Copia el Enum 'Archetype' al paquete 'enums' de este servicio.
    @Enumerated(EnumType.STRING)
    @Column(name = "archetype")
    private Archetype archetype;

    // -------------------------------------------------------------------
    // 🚦 ESTADO DEL ONBOARDING (Flags)
    // -------------------------------------------------------------------
    @Column(name = "is_kyc_verified")
    private boolean isKycVerified;

    @Column(name = "is_license_verified")
    private boolean isLicenseVerified;

    @Column(name = "is_marketplace_configured")
    private boolean isMarketplaceConfigured;

    @Column(name = "onboarding_complete")
    private boolean onboardingComplete;

    @Column(name = "trial_expires_at")
    private LocalDateTime trialExpiresAt;

    // -------------------------------------------------------------------
    // 🔗 RELACIONES (Lo que nos interesa ahora)
    // -------------------------------------------------------------------
    
    @ManyToMany(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
        name = "provider_tags",
        joinColumns = @JoinColumn(name = "provider_id"),
        inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Set<Tag> tags = new HashSet<>();
}
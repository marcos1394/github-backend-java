package com.quhealthy.onboarding_service.model;

import jakarta.persistence.*;
import lombok.*;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "provider_profiles")
public class ProviderProfile extends BaseEntity {

    @Id
    @Column(name = "provider_id")
    private Long providerId; // PK compartida con la tabla 'users' del Auth Service

    // --- IDENTIDAD ---

    @Column(name = "business_name", nullable = false)
    private String businessName; // Ej: "Consultorio Dental Dr. House"

    @Column(name = "slug", unique = true)
    private String slug; // Ej: "consultorio-dental-dr-house-101"

    @Column(name = "bio", length = 1000)
    private String bio;

    @Column(name = "profile_image_url")
    private String profileImageUrl;

    // --- UBICACIÓN & GOOGLE MAPS ---

    @Column(name = "address", nullable = false)
    private String address;

    @Column(name = "latitude", nullable = false)
    private Double latitude;

    @Column(name = "longitude", nullable = false)
    private Double longitude;

    /**
     * ID de Google Places.
     * Vital para sincronizar reseñas y fotos en el futuro.
     */
    @Column(name = "google_place_id")
    private String googlePlaceId; // ✅ AHORA SÍ EXISTE

    // --- CONTACTO PÚBLICO ---

    @Column(name = "website_url")
    private String websiteUrl; // ✅ NUEVO

    @Column(name = "contact_phone")
    private String contactPhone; // ✅ NUEVO: Teléfono del negocio (distinto al personal)

    // --- CATEGORIZACIÓN ---

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(name = "sub_category_id", nullable = false)
    private Long subCategoryId;
}
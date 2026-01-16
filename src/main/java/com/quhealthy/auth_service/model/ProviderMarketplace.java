package com.quhealthy.auth_service.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
// import java.util.List; // Descomentar cuando tengas Services y Staff

@Data
@Entity
@Table(name = "provider_marketplaces")
public class ProviderMarketplace {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Relación 1 a 1 con Provider
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id", nullable = false, unique = true)
    private Provider provider;

    // --- Datos de la Tienda ---
    @Column(name = "store_name")
    private String storeName;

    @Column(name = "store_slug", unique = true)
    private String storeSlug;

    // --- Branding & UI (Mapeo exacto a tus campos snake_case) ---
    @Column(name = "store_logo_url")
    private String storeLogo;

    @Column(name = "store_banner_url")
    private String storeBanner;

    @Column(name = "primary_color")
    private String primaryColor;

    @Column(name = "secondary_color")
    private String secondaryColor;

    private String typography;

    @Column(name = "custom_description", columnDefinition = "TEXT")
    private String customDescription;

    @Column(name = "welcome_video_url")
    private String welcomeVideo;

    // --- SEO ---
    @Column(name = "meta_title")
    private String metaTitle;

    @Column(name = "meta_description")
    private String metaDescription;

    // --- Redes Sociales (JSONB) ---
    // Guardamos el JSON como String. Usaremos Jackson para leerlo/escribirlo en el DTO.
    @Column(name = "social_links", columnDefinition = "jsonb")
    private String socialLinks;

    // --- Relaciones Futuras (Comentadas para que compile ahora) ---
    // @OneToMany(mappedBy = "marketplace")
    // private List<ProviderService> services;

    // @OneToMany(mappedBy = "marketplace")
    // private List<ProviderStaff> staff;

    // --- Timestamps ---
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
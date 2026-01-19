package com.quhealthy.auth_service.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode; // <--- 1. IMPORTAR
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes; // <--- 2. IMPORTAR

import java.time.LocalDateTime;
import java.util.Map; // <--- 3. IMPORTAR
// import java.util.List; 

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

    // --- Branding & UI ---
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

    // =================================================================
    // 🔧 LA CORRECCIÓN: JSONB Mapping
    // =================================================================
    // Cambiamos String por Map<String, Object> para evitar el error de tipos en Postgres
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "social_links", columnDefinition = "jsonb")
    private Map<String, Object> socialLinks;

    // --- Relaciones Futuras ---
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
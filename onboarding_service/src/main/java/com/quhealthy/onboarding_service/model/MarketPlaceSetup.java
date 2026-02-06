package com.quhealthy.onboarding_service.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "provider_marketplace_setup")
public class MarketPlaceSetup extends BaseEntity {

    @Id
    @Column(name = "provider_id")
    private Long providerId; // ✅ CORREGIDO: Usamos Long para coincidir con Auth Service

    // --- INFORMACIÓN PÚBLICA ---

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "short_description", length = 300)
    private String shortDescription;

    @Column(name = "contact_phone")
    private String contactPhone;

    @Column(name = "website_url")
    private String websiteUrl;

    // --- CONFIGURACIÓN DE SERVICIOS (Menú Inicial) ---

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "initial_services", columnDefinition = "jsonb")
    private List<ServiceDraft> initialServices; // ✅ Ahora sí reconocerá la clase ServiceDraft

    // --- GALERÍA ---

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "gallery_images", columnDefinition = "jsonb")
    private List<String> galleryImages;

    // --- HORARIOS ---

    @Column(name = "opening_hours_summary")
    private String openingHoursSummary;
}
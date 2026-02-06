package com.quhealthy.catalog_service.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "store_profiles")
public class StoreProfile {

    @Id
    @Column(name = "provider_id")
    private Long providerId; // Vinculación 1 a 1 con el Doctor

    @Column(unique = true, nullable = false)
    private String slug; // Ej: "cardiologia-avanzada" (Para la URL pública)

    @Column(name = "display_name", nullable = false)
    private String displayName; // Nombre comercial de la tienda

    @Column(name = "logo_url")
    private String logoUrl;

    @Column(name = "banner_url")
    private String bannerUrl;

    @Column(name = "primary_color", length = 7)
    private String primaryColor; // Hex Color: #FFFFFF

    @Column(columnDefinition = "TEXT")
    private String bio;

    // --- Preferencias ---
    @Builder.Default
    private boolean whatsappEnabled = true;
}
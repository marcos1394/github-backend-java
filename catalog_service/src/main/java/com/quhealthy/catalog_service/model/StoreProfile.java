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

    @Column(unique = true)
    private String slug; // Ej: "cardiologia-avanzada" (Para la URL pública)

    @Column(name = "display_name")
    private String displayName; // Nombre comercial de la tienda

    @Column(name = "logo_url")
    private String logoUrl;

    @Column(name = "banner_url")
    private String bannerUrl;

    @Column(name = "primary_color", length = 7)
    private String primaryColor; // Hex Color: #FFFFFF

    // ✅ CAMPO FALTANTE 1: Color Secundario
    @Column(name = "secondary_color", length = 7)
    private String secondaryColor;

    @Column(columnDefinition = "TEXT")
    private String bio;

    // --- Preferencias ---

    @Builder.Default
    @Column(name = "whatsapp_enabled")
    private boolean whatsappEnabled = true;

    // ✅ CAMPO FALTANTE 2: Mostrar ubicación en el perfil público
    @Builder.Default
    @Column(name = "show_location")
    private boolean showLocation = true;

    // ✅ CAMPO FALTANTE 3: Control de acceso al Marketplace (Gestionado por el Plan)
    // Si es false, la tienda existe pero no sale en búsquedas globales "/nearby"
    @Builder.Default
    @Column(name = "marketplace_visible")
    private boolean marketplaceVisible = false;
}
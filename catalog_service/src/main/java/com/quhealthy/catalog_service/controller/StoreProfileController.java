package com.quhealthy.catalog_service.controller;

import com.quhealthy.catalog_service.config.CustomAuthenticationToken;
import com.quhealthy.catalog_service.model.StoreProfile;
import com.quhealthy.catalog_service.service.CatalogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/store/profile")
@RequiredArgsConstructor
public class StoreProfileController {

    private final CatalogService catalogService;

    /**
     * 🎨 Actualizar Branding (Logo, Colores, Bio).
     * Solo el dueño de la tienda puede hacer esto.
     */
    @PutMapping("/me")
    @PreAuthorize("hasRole('PROVIDER')")
    public ResponseEntity<StoreProfile> updateMyBranding(@RequestBody @Valid StoreProfile profileRequest) {
        CustomAuthenticationToken session = getSession();
        Long providerId = (Long) session.getPrincipal();

        log.info("🎨 Actualizando branding para tienda de Provider ID: {}", providerId);

        // Forzamos el ID del provider autenticado para evitar suplantación
        // (Aunque el request traiga otro ID, usamos el del token)
        StoreProfile updatedProfile = catalogService.updateStoreBranding(providerId, profileRequest);

        return ResponseEntity.ok(updatedProfile);
    }

    /**
     * 👁️ Ver Perfil de Tienda (Público).
     * Usado cuando un paciente entra a "quhealthy.com/store/dr-house".
     * Carga el logo, banner y colores antes de cargar los productos.
     */
    @GetMapping("/{providerId}")
    public ResponseEntity<StoreProfile> getStoreBranding(@PathVariable Long providerId) {
        return ResponseEntity.ok(catalogService.getStoreProfile(providerId));
    }

    // ========================================================================
    // 🛠️ UTILS
    // ========================================================================

    private CustomAuthenticationToken getSession() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof CustomAuthenticationToken) {
            return (CustomAuthenticationToken) auth;
        }
        throw new SecurityException("Sesión no válida o expirada.");
    }
}
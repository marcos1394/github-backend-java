package com.quhealthy.catalog_service.controller;

import com.quhealthy.catalog_service.config.CustomAuthenticationToken;
import com.quhealthy.catalog_service.dto.CatalogItemRequest;
import com.quhealthy.catalog_service.dto.CatalogItemResponse;
import com.quhealthy.catalog_service.service.CatalogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/catalog")
@RequiredArgsConstructor
public class CatalogController {

    private final CatalogService catalogService;

    // ========================================================================
    // 🔐 GESTIÓN DEL PROVEEDOR (Requiere Token PROVIDER)
    // ========================================================================

    @PostMapping("/items")
    @PreAuthorize("hasRole('PROVIDER')")
    public ResponseEntity<CatalogItemResponse> createItem(@Valid @RequestBody CatalogItemRequest request) {
        CustomAuthenticationToken session = getSession();
        Long providerId = (Long) session.getPrincipal();
        Long planId = session.getPlanId();

        log.info("📝 Provider {} (Plan {}) creando ítem: {}", providerId, planId, request.getName());

        CatalogItemResponse response = catalogService.createItem(providerId, request, planId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/items/{id}")
    @PreAuthorize("hasRole('PROVIDER')")
    public ResponseEntity<CatalogItemResponse> updateItem(
            @PathVariable Long id,
            @Valid @RequestBody CatalogItemRequest request
    ) {
        Long providerId = (Long) getSession().getPrincipal();
        return ResponseEntity.ok(catalogService.updateItem(providerId, id, request));
    }

    @DeleteMapping("/items/{id}")
    @PreAuthorize("hasRole('PROVIDER')")
    public ResponseEntity<Void> deleteItem(@PathVariable Long id) {
        Long providerId = (Long) getSession().getPrincipal();
        catalogService.deleteItem(providerId, id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Dashboard del Doctor: "Mis Servicios".
     * Devuelve todo su catálogo (Activo, Pausado, etc).
     */
    @GetMapping("/me/items")
    @PreAuthorize("hasRole('PROVIDER')")
    public ResponseEntity<Page<CatalogItemResponse>> getMyCatalog(
            @PageableDefault(size = 20, sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Long providerId = (Long) getSession().getPrincipal();
        // Reutilizamos la búsqueda por provider, pero en el futuro podríamos querer ver también los archivados.
        // Por ahora, ver lo que ve el público es suficiente para el MVP.
        return ResponseEntity.ok(catalogService.getProviderCatalog(providerId, null, pageable));
    }

    // ========================================================================
    // 🌍 BÚSQUEDA PÚBLICA Y DISCOVERY (Pacientes & Marketplace)
    // ========================================================================

    /**
     * Detalle de un Producto/Servicio.
     * @param lat Latitud del usuario (opcional, para calcular "a X km de ti")
     * @param lng Longitud del usuario (opcional)
     */
    @GetMapping("/items/{id}")
    public ResponseEntity<CatalogItemResponse> getItemDetail(
            @PathVariable Long id,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng
    ) {
        return ResponseEntity.ok(catalogService.getItemDetail(id, lat, lng));
    }

    /**
     * 🛰️ MARKETPLACE: "Cerca de Mí".
     * Busca ítems geo-localizados.
     */
    @GetMapping("/nearby")
    public ResponseEntity<Page<CatalogItemResponse>> getNearbyItems(
            @RequestParam Double lat,
            @RequestParam Double lng,
            @RequestParam(defaultValue = "10.0") Double radiusKm,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(catalogService.getNearbyItems(lat, lng, radiusKm, pageable));
    }

    /**
     * 🔍 BUSCADOR GLOBAL (Texto).
     * Busca por nombre o tags dentro del catálogo de un proveedor específico o global (si ajustamos el service).
     * Por ahora el service pide providerId, así que lo usamos como "Buscador dentro de la tienda del Dr. X".
     */
    @GetMapping("/provider/{providerId}/search")
    public ResponseEntity<Page<CatalogItemResponse>> searchInStore(
            @PathVariable Long providerId,
            @RequestParam String q,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(catalogService.searchGlobal(providerId, q, pageable));
    }

    /**
     * 🏪 TIENDA DEL DOCTOR (Perfil Público).
     * Lista todos los servicios de un doctor, con filtro opcional por categoría.
     */
    @GetMapping("/provider/{providerId}/items")
    public ResponseEntity<Page<CatalogItemResponse>> getProviderStore(
            @PathVariable Long providerId,
            @RequestParam(required = false) String category, // Ej: "CONSULTA", "SUPLEMENTOS"
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(catalogService.getProviderCatalog(providerId, category, pageable));
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
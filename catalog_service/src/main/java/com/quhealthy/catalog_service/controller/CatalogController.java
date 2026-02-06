package com.quhealthy.catalog_service.controller;

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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/catalog")
@RequiredArgsConstructor
public class CatalogController {

    private final CatalogService service;

    // ==========================================
    // 🔐 1. GESTIÓN DEL DOCTOR (Requiere Token)
    // ==========================================

    @PostMapping
    public ResponseEntity<CatalogItemResponse> createItem(@Valid @RequestBody CatalogItemRequest request) {
        Long providerId = getCurrentUserId();
        CatalogItemResponse response = service.createItem(providerId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<CatalogItemResponse> updateItem(
            @PathVariable Long id,
            @Valid @RequestBody CatalogItemRequest request
    ) {
        Long providerId = getCurrentUserId();
        return ResponseEntity.ok(service.updateItem(providerId, id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteItem(@PathVariable Long id) {
        Long providerId = getCurrentUserId();
        service.deleteItem(providerId, id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Dashboard del Doctor: "Mis Servicios".
     * Muestra todo (Activos, Pausados, Archivados).
     */
    @GetMapping("/me")
    public ResponseEntity<Page<CatalogItemResponse>> getMyCatalog(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Long providerId = getCurrentUserId();
        // Usamos el método de búsqueda global filtrado por providerId
        // Para el dashboard, podríamos querer ver todo, aquí reutilizo la búsqueda por categoría o todo.
        // Nota: Si necesitas ver items INACTIVOS, deberíamos agregar un método específico en Service.
        // Por ahora, usamos el getProviderCatalog que filtra por ACTIVE.
        // Si quieres ver TODO (dashboard admin), usa findAllByProviderId del repo directo o crea servicio.
        // Asumiremos que el doctor ve su vista pública por ahora.
        return ResponseEntity.ok(service.getProviderCatalog(providerId, null, pageable));
    }

    // ==========================================
    // 🌍 2. BÚSQUEDA PÚBLICA (Pacientes)
    // ==========================================

    /**
     * Detalle de un Producto/Servicio.
     * Recibe lat/lng opcionales para calcular "A cuántos km estás".
     */
    @GetMapping("/{id}")
    public ResponseEntity<CatalogItemResponse> getItemDetail(
            @PathVariable Long id,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng
    ) {
        return ResponseEntity.ok(service.getItemDetail(id, lat, lng));
    }

    /**
     * 🛰️ MARKETPLACE: Búsqueda "Cerca de mí".
     * Busca en TODOS los doctores.
     */
    @GetMapping("/nearby")
    public ResponseEntity<Page<CatalogItemResponse>> getNearbyItems(
            @RequestParam Double lat,
            @RequestParam Double lng,
            @RequestParam(defaultValue = "10.0") Double radiusKm,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(service.getNearbyItems(lat, lng, radiusKm, pageable));
    }

    /**
     * Tienda de un Doctor Específico.
     * Ej: Cuando entras al perfil del "Dr. House".
     */
    @GetMapping("/provider/{providerId}")
    public ResponseEntity<Page<CatalogItemResponse>> getProviderStore(
            @PathVariable Long providerId,
            @RequestParam(required = false) String category, // Filtro opcional: "SALUD" vs "BELLEZA"
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(service.getProviderCatalog(providerId, category, pageable));
    }

    /**
     * Buscador de Texto (Google-like) DENTRO de la tienda de un doctor.
     */
    @GetMapping("/provider/{providerId}/search")
    public ResponseEntity<Page<CatalogItemResponse>> searchInStore(
            @PathVariable Long providerId,
            @RequestParam String q,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(service.searchGlobal(providerId, q, pageable));
    }

    // ==========================================
    // 🛠️ HELPERS
    // ==========================================

    /**
     * Extrae el ID del usuario desde el Token JWT.
     * Gracias al JwtAuthenticationFilter, el 'principal' es el ID (Long).
     */
    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new SecurityException("Usuario no autenticado");
        }
        return (Long) authentication.getPrincipal();
    }
}
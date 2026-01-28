package com.quhealthy.catalog_service.controller;

import com.quhealthy.catalog_service.dto.request.CreatePackageRequest;
import com.quhealthy.catalog_service.dto.request.CreateServiceRequest;
import com.quhealthy.catalog_service.dto.response.PackageResponse;
import com.quhealthy.catalog_service.dto.response.ProviderCatalogResponse;
import com.quhealthy.catalog_service.dto.response.ServiceResponse;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/catalog")
@RequiredArgsConstructor
public class CatalogController {

    private final CatalogService catalogService;

    // =================================================================
    // 🔒 ZONA PRIVADA (Solo Doctores Autenticados)
    // =================================================================

    /**
     * ✅ CREAR SERVICIO
     * Endpoint: POST /api/catalog/services
     * Body: { "name": "Consulta", "price": 500, "durationMinutes": 30 }
     */
    @PostMapping("/services")
    public ResponseEntity<ServiceResponse> createService(
            @AuthenticationPrincipal Long providerId, // 👈 ID extraído del Token JWT
            @Valid @RequestBody CreateServiceRequest request) {
        
        log.info("📥 Request Crear Servicio recibida del Provider: {}", providerId);
        ServiceResponse response = catalogService.createService(providerId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * ✅ CREAR PAQUETE (COMBO)
     * Endpoint: POST /api/catalog/packages
     * Body: { "name": "Checkup", "price": 800, "serviceIds": [1, 2] }
     */
    @PostMapping("/packages")
    public ResponseEntity<PackageResponse> createPackage(
            @AuthenticationPrincipal Long providerId,
            @Valid @RequestBody CreatePackageRequest request) {
        
        log.info("📥 Request Crear Paquete recibida del Provider: {}", providerId);
        PackageResponse response = catalogService.createPackage(providerId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * ✅ DASHBOARD: MIS SERVICIOS (Paginado)
     * Endpoint: GET /api/catalog/myservices
     * Uso: Para que el doctor vea su tabla de precios en el admin panel.
     */
    @GetMapping("/myservices")
    public ResponseEntity<Page<ServiceResponse>> getMyServices(
            @AuthenticationPrincipal Long providerId,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        
        return ResponseEntity.ok(catalogService.getMyServices(providerId, pageable));
    }

    // =================================================================
    // 🔓 ZONA PÚBLICA (Pacientes / Visitantes)
    // =================================================================

    /**
     * ✅ PERFIL PÚBLICO: VER CATÁLOGO COMPLETO
     * Endpoint: GET /api/catalog/provider/{providerId}
     * Retorna: Servicios individuales + Paquetes con cálculo de ahorro.
     * Uso: Cuando un paciente entra al perfil de un doctor.
     */
    @GetMapping("/provider/{providerId}")
    public ResponseEntity<ProviderCatalogResponse> getPublicCatalog(@PathVariable Long providerId) {
        return ResponseEntity.ok(catalogService.getPublicCatalog(providerId));
    }
}
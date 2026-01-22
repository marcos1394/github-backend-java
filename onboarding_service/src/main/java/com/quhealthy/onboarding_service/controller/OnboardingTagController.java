package com.quhealthy.onboarding_service.controller;

import com.quhealthy.onboarding_service.dto.TagDto;
import com.quhealthy.onboarding_service.dto.UpdateTagsRequest;
import com.quhealthy.onboarding_service.model.Provider;
import com.quhealthy.onboarding_service.repository.ProviderRepository;
import com.quhealthy.onboarding_service.service.OnboardingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/onboarding/tags")
@RequiredArgsConstructor
public class OnboardingTagController {

    private final OnboardingService onboardingService;
    private final ProviderRepository providerRepository; // 💉 Inyectado para buscar por Email

    // GET /api/onboarding/tags/catalog
    // Obtiene todas las etiquetas disponibles en el sistema (Globales)
    @GetMapping("/catalog")
    public ResponseEntity<List<TagDto>> getCatalog() {
        return ResponseEntity.ok(onboardingService.getAllGlobalTags());
    }

    // GET /api/onboarding/tags/my-tags
    // Obtiene las etiquetas que el proveedor ya ha seleccionado
    @GetMapping("/my-tags")
    public ResponseEntity<List<TagDto>> getMyTags() {
        return ResponseEntity.ok(onboardingService.getProviderTags(getAuthenticatedId()));
    }

    // PUT /api/onboarding/tags/my-tags
    // Actualiza la selección de etiquetas (Reemplaza las anteriores con las nuevas)
    @PutMapping("/my-tags")
    public ResponseEntity<String> updateMyTags(@RequestBody UpdateTagsRequest request) {
        onboardingService.updateProviderTags(getAuthenticatedId(), request.getTagIds());
        return ResponseEntity.ok("Etiquetas actualizadas correctamente.");
    }

    // DELETE /api/onboarding/tags/my-tags
    // Elimina todas las etiquetas asociadas al proveedor
    @DeleteMapping("/my-tags")
    public ResponseEntity<String> deleteMyTags() {
        onboardingService.removeAllProviderTags(getAuthenticatedId());
        return ResponseEntity.ok("Etiquetas eliminadas.");
    }

    // -----------------------------------------------------------
    // 🔐 HELPER DE SEGURIDAD CORREGIDO
    // -----------------------------------------------------------
    private Long getAuthenticatedId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        
        // 1. Validar que exista autenticación básica
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new RuntimeException("Usuario no autenticado.");
        }

        // 2. Extraer el Email (El 'subject' del token JWT generado por Auth Service es el email)
        String emailFromToken = auth.getName();
        log.debug("🔐 Procesando petición para usuario (Email del Token): {}", emailFromToken);

        // 3. Buscar el ID en la base de datos usando el Email
        // Esto soluciona el error de NumberFormatException porque ya no casteamos el email a Long directamente.
        Provider provider = providerRepository.findByEmail(emailFromToken)
                .orElseThrow(() -> {
                    log.error("❌ El usuario con email {} viene en el token pero NO existe en la tabla providers.", emailFromToken);
                    return new RuntimeException("Usuario no encontrado en la base de datos de Onboarding.");
                });

        return provider.getId();
    }
}
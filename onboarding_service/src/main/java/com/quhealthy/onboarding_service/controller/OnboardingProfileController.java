package com.quhealthy.onboarding_service.controller;

import com.quhealthy.onboarding_service.dto.UpdateProfileRequest;
import com.quhealthy.onboarding_service.model.Provider;
import com.quhealthy.onboarding_service.repository.ProviderRepository;
import com.quhealthy.onboarding_service.service.OnboardingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/onboarding/profile")
@RequiredArgsConstructor
public class OnboardingProfileController {

    private final OnboardingService onboardingService;
    private final ProviderRepository providerRepository;

    /**
     * PUT /api/onboarding/profile/business-info
     * Endpoint para el "Paso Intermedio": Llenar datos faltantes (Teléfono, Nombre Negocio, etc.)
     * No requiere dirección ni mapas.
     */
    @PutMapping("/business-info")
    public ResponseEntity<?> updateBusinessInfo(@Valid @RequestBody UpdateProfileRequest request) {
        Long providerId = getAuthenticatedId();
        
        onboardingService.updateBusinessProfile(providerId, request);
        
        return ResponseEntity.ok("Información de negocio básica actualizada. Listo para el siguiente paso.");
    }

    // 🔐 Helper de Seguridad: Obtiene el ID del proveedor basado en el Email del Token
    private Long getAuthenticatedId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new RuntimeException("Usuario no autenticado.");
        }

        String email = auth.getName(); // El token JWT trae el email en el 'subject'
        
        return providerRepository.findByEmail(email)
                .map(Provider::getId)
                .orElseThrow(() -> {
                    log.error("❌ Token válido pero usuario no encontrado en DB Onboarding: {}", email);
                    return new RuntimeException("Usuario no encontrado en la base de datos.");
                });
    }
}
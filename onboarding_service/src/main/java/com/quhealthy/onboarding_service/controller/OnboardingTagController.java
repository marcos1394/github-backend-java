package com.quhealthy.onboarding_service.controller;

import com.quhealthy.onboarding_service.dto.TagDto;
import com.quhealthy.onboarding_service.dto.UpdateTagsRequest;
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

    // GET /api/onboarding/tags/catalog
    @GetMapping("/catalog")
    public ResponseEntity<List<TagDto>> getCatalog() {
        return ResponseEntity.ok(onboardingService.getAllGlobalTags());
    }

    // GET /api/onboarding/tags/my-tags
    @GetMapping("/my-tags")
    public ResponseEntity<List<TagDto>> getMyTags() {
        return ResponseEntity.ok(onboardingService.getProviderTags(getAuthenticatedId()));
    }

    // PUT /api/onboarding/tags/my-tags
    @PutMapping("/my-tags")
    public ResponseEntity<String> updateMyTags(@RequestBody UpdateTagsRequest request) {
        onboardingService.updateProviderTags(getAuthenticatedId(), request.getTagIds());
        return ResponseEntity.ok("Etiquetas actualizadas correctamente.");
    }

    // DELETE /api/onboarding/tags/my-tags
    @DeleteMapping("/my-tags")
    public ResponseEntity<String> deleteMyTags() {
        onboardingService.removeAllProviderTags(getAuthenticatedId());
        return ResponseEntity.ok("Etiquetas eliminadas.");
    }

    // -----------------------------------------------------------
    // 🔐 HELPER DE SEGURIDAD
    // -----------------------------------------------------------
    private Long getAuthenticatedId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            // Esto debería capturarlo el filtro de seguridad antes, pero por si acaso:
            throw new RuntimeException("Usuario no autenticado.");
        }

        try {
            // Asumimos que el JWT Filter pone el ID del usuario como 'Principal' o 'Name'
            return Long.valueOf(auth.getName());
        } catch (NumberFormatException e) {
            log.error("❌ Error extrayendo ID del token. Valor recibido: {}", auth.getName());
            throw new RuntimeException("Token inválido: No se pudo identificar al usuario.");
        }
    }
}
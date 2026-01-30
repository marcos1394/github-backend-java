package com.quhealthy.payment_service.controller;

import com.quhealthy.payment_service.service.StripeCheckoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/payments") // 1. Usamos la ruta base general
@RequiredArgsConstructor
public class PaymentSettingsController {

    private final StripeCheckoutService checkoutService;

    // 2. Blindaje: Agregamos un valor por defecto para evitar que falle si falta la variable
    @Value("${application.frontend.url:https://quhealthy.org}")
    private String frontendUrl;

    /**
     * Endpoint para generar la sesión del Portal de Facturación del Cliente.
     * * ⚠️ CORRECCIÓN DE RUTA:
     * Cambiamos de "/portal" a "/billing-portal" para solucionar el error:
     * "Ambiguous mapping" con PaymentController.
     */
    @PostMapping("/billing-portal")
    public ResponseEntity<Map<String, String>> createPortalSession(@AuthenticationPrincipal Long userId) {
        log.info("Generando sesión de portal de facturación para usuario ID: {}", userId);

        // Construimos la URL de retorno
        String returnUrl = frontendUrl + "/profile/settings";
        
        // Llamamos al servicio
        String url = checkoutService.createCustomerPortalSession(userId, returnUrl);
        
        return ResponseEntity.ok(Map.of("url", url));
    }
}
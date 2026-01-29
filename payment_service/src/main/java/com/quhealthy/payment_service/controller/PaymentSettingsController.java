package com.quhealthy.payment_service.controller;

import com.quhealthy.payment_service.service.StripeCheckoutService;
import com.quhealthy.payment_service.service.StripeIdentityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/payments/portal")
@RequiredArgsConstructor
public class PaymentSettingsController {

    private final StripeCheckoutService checkoutService;
    private final StripeIdentityService identityService;

    @Value("${application.frontend.url}")
    private String frontendUrl;

    /**
     * ⚙️ PORTAL DE FACTURACIÓN (Para Doctores y Pacientes)
     * Redirige al usuario a Stripe para que administre sus tarjetas y facturas.
     */
    @PostMapping
    public ResponseEntity<Map<String, String>> createPortalSession(
            @AuthenticationPrincipal Long userId) {
        
        // 1. Buscamos su ID de Cliente
        // (El email/name es irrelevante aquí porque solo buscamos, no creamos)
        String customerId = identityService.getOrCreateCustomer(userId, null, null);

        // 2. Generamos la URL del portal
        // Si el usuario cierra el portal, vuelve a su perfil
        String portalUrl = checkoutService.createCustomerPortalSession(customerId, frontendUrl + "/profile/settings");

        return ResponseEntity.ok(Map.of("url", portalUrl));
    }
}
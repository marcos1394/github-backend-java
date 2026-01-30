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
@RequestMapping("/api/payments/portal")
@RequiredArgsConstructor
public class PaymentSettingsController {

    private final StripeCheckoutService checkoutService;

    @Value("${application.frontend.url}")
    private String frontendUrl;

    @PostMapping
    public ResponseEntity<Map<String, String>> createPortalSession(@AuthenticationPrincipal Long userId) {
        // ✅ CORRECCIÓN: Pasamos userId (Long) directamente.
        String url = checkoutService.createCustomerPortalSession(userId, frontendUrl + "/profile/settings");
        return ResponseEntity.ok(Map.of("url", url));
    }
}
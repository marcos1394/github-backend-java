package com.quhealthy.payment_service.controller;

import com.quhealthy.payment_service.service.StripeCheckoutService;
import com.stripe.model.checkout.Session;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/payments/checkout")
@RequiredArgsConstructor
public class PaymentCheckoutController {

    private final StripeCheckoutService checkoutService;

    // =================================================================
    // 🩺 1. PAGAR UNA CITA (PACIENTE)
    // =================================================================
    @PostMapping("/appointment")
    public ResponseEntity<Map<String, String>> checkoutAppointment(
            @AuthenticationPrincipal Long patientId, // ID del JWT (Paciente)
            @RequestBody AppointmentCheckoutRequest request) {

        log.info("🛒 Iniciando Checkout Cita #{}", request.getAppointmentId());

        // NOTA: En un caso real, aquí deberías llamar a AppointmentService 
        // para validar que la cita existe y obtener el precio real de la BD 
        // para evitar que el frontend te mande un precio falso ($1.00).
        // Por ahora, asumimos que los datos vienen validados o confiables del DTO.

        Session session = checkoutService.createAppointmentCheckout(
                request.getAppointmentId(),
                patientId, 
                request.getPatientEmail(), 
                request.getPatientName(),
                request.getProviderId(), 
                request.getProviderPlanId(),
                request.getTotalAmount(), 
                "mxn"
        );

        return ResponseEntity.ok(Map.of("url", session.getUrl()));
    }

    // =================================================================
    // 🛒 2. PAGAR SUSCRIPCIÓN (DOCTOR)
    // =================================================================
    @PostMapping("/subscription")
    public ResponseEntity<Map<String, String>> checkoutSubscription(
            @AuthenticationPrincipal Long providerId, // ID del JWT (Doctor)
            @RequestBody SubscriptionCheckoutRequest request) {

        log.info("🛒 Iniciando Checkout Suscripción Plan: {}", request.getPriceId());

        Session session = checkoutService.createSubscriptionCheckout(
                providerId,
                request.getEmail(),
                request.getName(),
                request.getPriceId(),
                request.getSuccessUrl(),
                request.getCancelUrl(),
                request.getTrialDays()
        );

        return ResponseEntity.ok(Map.of("url", session.getUrl()));
    }

    // --- DTOs (Data Transfer Objects) Internos ---
    
    @Data
    public static class AppointmentCheckoutRequest {
        private Long appointmentId;
        private Long providerId;
        private Long providerPlanId; // ID numérico del plan (1, 2, 3...)
        private String patientEmail;
        private String patientName;
        private BigDecimal totalAmount;
    }

    @Data
    public static class SubscriptionCheckoutRequest {
        private String priceId; // ID de Stripe (price_1Rnp...)
        private String email;
        private String name;
        private String successUrl;
        private String cancelUrl;
        private Integer trialDays;
    }
}
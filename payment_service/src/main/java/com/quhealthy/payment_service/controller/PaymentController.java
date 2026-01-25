package com.quhealthy.payment_service.controller;

import com.mercadopago.resources.preapproval.Preapproval;
import com.quhealthy.payment_service.dto.CreateCheckoutRequest;
import com.quhealthy.payment_service.model.Subscription;
import com.quhealthy.payment_service.repository.SubscriptionRepository;
import com.quhealthy.payment_service.service.MercadoPagoService;
import com.quhealthy.payment_service.service.StripeService;
import com.stripe.model.checkout.Session;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final StripeService stripeService;
    private final MercadoPagoService mercadoPagoService;
    private final SubscriptionRepository subscriptionRepository;

    // ==========================================
    // 1. INICIAR COMPRA (Checkout)
    // ==========================================
    @PostMapping("/checkout")
    public ResponseEntity<?> createCheckout(@Valid @RequestBody CreateCheckoutRequest request) {
        Long providerId = getAuthenticatedUserId();
        // En producción, obtén el email real del usuario vía Auth Service
        String userEmail = "provider_" + providerId + "@quhealthy.com"; 

        if (request.getGateway() == com.quhealthy.payment_service.model.enums.PaymentGateway.STRIPE) {
            
            // Buscar ID de cliente previo para no duplicarlo
            String existingCustomerId = subscriptionRepository.findByProviderIdOrderByCreatedAtDesc(providerId)
                    .stream()
                    .map(Subscription::getExternalCustomerId)
                    .findFirst()
                    .orElse(null);

            // CORRECCIÓN: Definimos trialDays como null (Modelo Freemium, pago inmediato)
            // Si en el futuro quieres dar 7 días gratis, cambias esto a 7.
            Integer trialDays = null;

            Session session = stripeService.createSubscriptionCheckout(
                    providerId,
                    userEmail,
                    request.getPlanId(),
                    request.getSuccessUrl(),
                    request.getCancelUrl(),
                    existingCustomerId,
                    trialDays // 👈 AGREGADO: El 7mo argumento que faltaba
            );
            return ResponseEntity.ok(Map.of("url", session.getUrl()));

        } else if (request.getGateway() == com.quhealthy.payment_service.model.enums.PaymentGateway.MERCADOPAGO) {
            
            BigDecimal price = new BigDecimal("200.00"); // Ejemplo
            
            Preapproval preapproval = mercadoPagoService.createSubscription(
                    providerId,
                    userEmail,
                    request.getSuccessUrl(),
                    request.getPlanId(),
                    price
            );
            return ResponseEntity.ok(Map.of("url", preapproval.getInitPoint()));
        }

        return ResponseEntity.badRequest().body("Pasarela no soportada");
    }

    // ==========================================
    // 2. PORTAL DE CLIENTE (Self-Service)
    // ==========================================
    @PostMapping("/portal")
    public ResponseEntity<?> createCustomerPortal(@RequestBody Map<String, String> body) {
        Long providerId = getAuthenticatedUserId();
        String returnUrl = body.getOrDefault("returnUrl", "https://quhealthy.com/dashboard");

        Optional<Subscription> lastSub = subscriptionRepository.findByProviderIdOrderByCreatedAtDesc(providerId)
                .stream().findFirst();

        if (lastSub.isPresent() && lastSub.get().getExternalCustomerId() != null) {
            String url = stripeService.createCustomerPortalSession(lastSub.get().getExternalCustomerId(), returnUrl);
            return ResponseEntity.ok(Map.of("url", url));
        }

        return ResponseEntity.badRequest().body("No tienes historial de facturación para acceder al portal.");
    }

    // ==========================================
    // 3. CAMBIAR PLAN (Upgrade/Downgrade)
    // ==========================================
    @PostMapping("/subscription/change-plan")
    public ResponseEntity<?> changePlan(@RequestBody Map<String, String> body) {
        Long providerId = getAuthenticatedUserId();
        String newPlanId = body.get("newPlanId");

        Optional<Subscription> activeSub = subscriptionRepository.findActiveSubscription(providerId);

        if (activeSub.isPresent()) {
            Subscription sub = activeSub.get();
            if (sub.getGateway() == com.quhealthy.payment_service.model.enums.PaymentGateway.STRIPE) {
                stripeService.changeSubscriptionPlan(sub.getExternalSubscriptionId(), newPlanId);
                return ResponseEntity.ok(Map.of("message", "Plan actualizado correctamente."));
            }
        }
        return ResponseEntity.badRequest().body("No tienes una suscripción activa o la pasarela no soporta cambio directo.");
    }

    // 🔒 Helper User ID
    private Long getAuthenticatedUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (Long) auth.getPrincipal();
    }
}
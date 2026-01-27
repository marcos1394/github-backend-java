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
import java.time.Instant;
import java.time.LocalDateTime; // 👈 ESTE ERA EL IMPORT QUE FALTABA
import java.time.ZoneId;
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

    // ==========================================
    // 4. SINCRONIZACIÓN MANUAL (Fail-safe Enterprise)
    // ==========================================
    /**
     * Fuerza una consulta a Stripe para alinear el estado local con la realidad.
     * Útil si fallan los webhooks o si el sistema estuvo caído.
     * Actualiza: Estado, Fecha Fin, Fecha Inicio y Plan (si hubo cambio externo).
     */
    @GetMapping("/subscription/sync")
    public ResponseEntity<?> syncSubscriptionStatus() {
        Long providerId = getAuthenticatedUserId();

        // 1. Buscamos la suscripción local más reciente
        Optional<Subscription> localSubOpt = subscriptionRepository.findByProviderIdOrderByCreatedAtDesc(providerId)
                .stream().findFirst();

        if (localSubOpt.isEmpty() || localSubOpt.get().getExternalSubscriptionId() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "No se encontró suscripción local para sincronizar."));
        }

        Subscription localSub = localSubOpt.get();

        // 2. Solo sincronizamos si es Stripe (MercadoPago tiene otra lógica)
        if (localSub.getGateway() == com.quhealthy.payment_service.model.enums.PaymentGateway.STRIPE) {
            try {
                // 3. Consultamos a la fuente de la verdad (Stripe API)
                // Asegúrate de tener este método público en tu StripeService
                com.stripe.model.Subscription stripeSub = stripeService.retrieveSubscription(localSub.getExternalSubscriptionId());
                
                boolean hasChanges = false;
                StringBuilder changesLog = new StringBuilder();

                // -------------------------------------------------------
                // A. Sincronización de ESTADO
                // -------------------------------------------------------
                com.quhealthy.payment_service.model.enums.SubscriptionStatus realStatus = mapStripeStatus(stripeSub.getStatus());
                if (localSub.getStatus() != realStatus) {
                    changesLog.append(String.format("[Status: %s -> %s] ", localSub.getStatus(), realStatus));
                    localSub.setStatus(realStatus);
                    hasChanges = true;
                }

                // -------------------------------------------------------
                // B. Sincronización de FECHAS (Crítico para el acceso)
                // -------------------------------------------------------
                // Stripe usa Unix Timestamp (segundos), Java usa LocalDateTime. Convertimos.
                LocalDateTime stripePeriodEnd = LocalDateTime.ofInstant(java.time.Instant.ofEpochSecond(stripeSub.getCurrentPeriodEnd()), java.time.ZoneId.systemDefault());
                LocalDateTime stripePeriodStart = LocalDateTime.ofInstant(java.time.Instant.ofEpochSecond(stripeSub.getCurrentPeriodStart()), java.time.ZoneId.systemDefault());

                // Validamos Period End (Tolerancia de segundos no importa, comparamos igualdad de objeto o diferencia significativa)
                if (!stripePeriodEnd.equals(localSub.getCurrentPeriodEnd())) {
                    changesLog.append(String.format("[End: %s -> %s] ", localSub.getCurrentPeriodEnd(), stripePeriodEnd));
                    localSub.setCurrentPeriodEnd(stripePeriodEnd);
                    hasChanges = true;
                }

                // Validamos Period Start
                if (!stripePeriodStart.equals(localSub.getCurrentPeriodStart())) {
                    localSub.setCurrentPeriodStart(stripePeriodStart);
                    hasChanges = true;
                }

                // -------------------------------------------------------
                // C. Sincronización de PLAN (Si lo cambiaron en Dashboard de Stripe)
                // -------------------------------------------------------
                // El Price ID está dentro de items -> data[0] -> price -> id
                String realPlanId = stripeSub.getItems().getData().get(0).getPrice().getId();
                if (!realPlanId.equals(localSub.getPlanId())) {
                    changesLog.append(String.format("[Plan: %s -> %s] ", localSub.getPlanId(), realPlanId));
                    localSub.setPlanId(realPlanId);
                    hasChanges = true;
                }

                // -------------------------------------------------------
                // D. Guardado y Respuesta
                // -------------------------------------------------------
                if (hasChanges) {
                    localSub.setUpdatedAt(LocalDateTime.now());
                    subscriptionRepository.save(localSub);
                    
                    log.warn("⚠️ Sincronización ejecutada para Provider {}. Cambios: {}", providerId, changesLog.toString());
                    
                    return ResponseEntity.ok(Map.of(
                        "message", "Sincronización completada con cambios.",
                        "changes", changesLog.toString(),
                        "status", realStatus,
                        "validUntil", stripePeriodEnd
                    ));
                } else {
                    return ResponseEntity.ok(Map.of(
                        "message", "La suscripción ya estaba perfectamente sincronizada.",
                        "status", localSub.getStatus()
                    ));
                }

            } catch (Exception e) {
                log.error("❌ Error al sincronizar suscripción de Stripe para Provider {}: {}", providerId, e.getMessage());
                return ResponseEntity.internalServerError().body(Map.of("error", "Error conectando con Stripe.", "details", e.getMessage()));
            }
        }

        return ResponseEntity.badRequest().body(Map.of("error", "Sincronización no soportada para esta pasarela."));
    }

    // --- Helpers Utilitarios ---

    private com.quhealthy.payment_service.model.enums.SubscriptionStatus mapStripeStatus(String status) {
        if (status == null) return com.quhealthy.payment_service.model.enums.SubscriptionStatus.PAST_DUE;
        switch (status) {
            case "active": return com.quhealthy.payment_service.model.enums.SubscriptionStatus.ACTIVE;
            case "past_due": return com.quhealthy.payment_service.model.enums.SubscriptionStatus.PAST_DUE;
            case "canceled": return com.quhealthy.payment_service.model.enums.SubscriptionStatus.CANCELED;
            case "trialing": return com.quhealthy.payment_service.model.enums.SubscriptionStatus.TRIALING;
            case "unpaid": return com.quhealthy.payment_service.model.enums.SubscriptionStatus.PAST_DUE;
            case "incomplete": return com.quhealthy.payment_service.model.enums.SubscriptionStatus.PENDING;
            case "paused": return com.quhealthy.payment_service.model.enums.SubscriptionStatus.PAST_DUE;
            default: return com.quhealthy.payment_service.model.enums.SubscriptionStatus.PAST_DUE;
        }
    }

}
package com.quhealthy.payment_service.controller;

import com.mercadopago.resources.preapproval.Preapproval;
import com.quhealthy.payment_service.dto.CreateCheckoutRequest;
import com.quhealthy.payment_service.model.Plan;
import com.quhealthy.payment_service.model.Subscription;
import com.quhealthy.payment_service.repository.PlanRepository;
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
import java.time.LocalDateTime;
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
    
    // 👇 NUEVO: Repositorio para buscar precios y nombres reales
    private final PlanRepository planRepository;

    // ==========================================
    // 1. INICIAR COMPRA (Checkout)
    // ==========================================
    @PostMapping("/checkout")
    public ResponseEntity<?> createCheckout(@Valid @RequestBody CreateCheckoutRequest request) {
        Long providerId = getAuthenticatedUserId();
        // En producción, obtén el email real del usuario vía Auth Service
        String userEmail = "provider_" + providerId + "@quhealthy.com"; 

        // -------------------------------------------------------------------
        // OPCIÓN A: STRIPE
        // -------------------------------------------------------------------
        if (request.getGateway() == com.quhealthy.payment_service.model.enums.PaymentGateway.STRIPE) {
            
            // Buscar ID de cliente previo para no duplicarlo
            String existingCustomerId = subscriptionRepository.findByProviderIdOrderByCreatedAtDesc(providerId)
                    .stream()
                    .map(Subscription::getExternalCustomerId)
                    .findFirst()
                    .orElse(null);

            // Modelo Freemium (pago inmediato, sin trial)
            Integer trialDays = null;

            Session session = stripeService.createSubscriptionCheckout(
                    providerId,
                    userEmail,
                    request.getPlanId(),
                    request.getSuccessUrl(),
                    request.getCancelUrl(),
                    existingCustomerId,
                    trialDays
            );
            return ResponseEntity.ok(Map.of("url", session.getUrl()));

        // -------------------------------------------------------------------
        // OPCIÓN B: MERCADO PAGO
        // -------------------------------------------------------------------
        } else if (request.getGateway() == com.quhealthy.payment_service.model.enums.PaymentGateway.MERCADOPAGO) {
            
            // 1. BUSCAR EL PLAN REAL EN BD (Seguridad y UX)
            // Usamos el ID de MP que manda el frontend (ej: b1dbf...) para buscar en nuestra tabla 'plans'
            Plan plan = planRepository.findByMpPlanId(request.getPlanId())
                    .orElseThrow(() -> new RuntimeException("El plan de MercadoPago solicitado no existe o no está configurado."));

            // 2. EXTRAER DATOS REALES
            BigDecimal realPrice = plan.getPrice(); // Ej: 450.00
            String realName = plan.getName();      // Ej: "Plan Básico"

            // 3. GENERAR LINK DE SUSCRIPCIÓN
            Preapproval preapproval = mercadoPagoService.createSubscription(
                    providerId,
                    userEmail,
                    request.getSuccessUrl(),
                    request.getPlanId(), // ID técnico de MP
                    realPrice,           // Precio Real de BD
                    realName             // Nombre Real de BD (para el título en checkout)
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

    // ==========================================
    // 4. SINCRONIZACIÓN MANUAL (Fail-safe Enterprise)
    // ==========================================
    /**
     * Fuerza una consulta a la pasarela (Stripe/MP) para alinear el estado local.
     * Útil si fallan los webhooks.
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

        // 2. Solo sincronizamos si es Stripe (MercadoPago tiene su propia lógica en desarrollo)
        if (localSub.getGateway() == com.quhealthy.payment_service.model.enums.PaymentGateway.STRIPE) {
            try {
                // 3. Consultamos a la fuente de la verdad (Stripe API)
                com.stripe.model.Subscription stripeSub = stripeService.retrieveSubscription(localSub.getExternalSubscriptionId());
                
                boolean hasChanges = false;
                StringBuilder changesLog = new StringBuilder();

                // A. Sincronización de ESTADO
                com.quhealthy.payment_service.model.enums.SubscriptionStatus realStatus = mapStripeStatus(stripeSub.getStatus());
                if (localSub.getStatus() != realStatus) {
                    changesLog.append(String.format("[Status: %s -> %s] ", localSub.getStatus(), realStatus));
                    localSub.setStatus(realStatus);
                    hasChanges = true;
                }

                // B. Sincronización de FECHAS
                LocalDateTime stripePeriodEnd = LocalDateTime.ofInstant(Instant.ofEpochSecond(stripeSub.getCurrentPeriodEnd()), ZoneId.systemDefault());
                LocalDateTime stripePeriodStart = LocalDateTime.ofInstant(Instant.ofEpochSecond(stripeSub.getCurrentPeriodStart()), ZoneId.systemDefault());

                if (!stripePeriodEnd.equals(localSub.getCurrentPeriodEnd())) {
                    changesLog.append(String.format("[End: %s -> %s] ", localSub.getCurrentPeriodEnd(), stripePeriodEnd));
                    localSub.setCurrentPeriodEnd(stripePeriodEnd);
                    hasChanges = true;
                }
                if (!stripePeriodStart.equals(localSub.getCurrentPeriodStart())) {
                    localSub.setCurrentPeriodStart(stripePeriodStart);
                    hasChanges = true;
                }

                // C. Sincronización de PLAN
                String realPlanId = stripeSub.getItems().getData().get(0).getPrice().getId();
                if (!realPlanId.equals(localSub.getPlanId())) {
                    changesLog.append(String.format("[Plan: %s -> %s] ", localSub.getPlanId(), realPlanId));
                    localSub.setPlanId(realPlanId);
                    hasChanges = true;
                }

                // D. Guardado
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
        
        // TODO: Implementar lógica similar para MercadoPago usando mercadoPagoService.getSubscription()
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

    // 🔒 Helper User ID
    private Long getAuthenticatedUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (Long) auth.getPrincipal();
    }
}
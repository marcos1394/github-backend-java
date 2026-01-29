package com.quhealthy.payment_service.controller;

import com.quhealthy.payment_service.repository.MerchantAccountRepository;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/payments/webhooks")
@RequiredArgsConstructor
public class StripeWebhookController {

    private final MerchantAccountRepository merchantRepository;

    @Value("${stripe.webhook.secret}")
    private String endpointSecret; // 🔐 Vital: Se obtiene del Dashboard de Stripe

    @PostMapping
    public ResponseEntity<String> handleStripeEvent(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {

        if (endpointSecret == null) {
            log.error("❌ No se ha configurado el secreto del Webhook.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

        Event event;

        try {
            // 1. Verificar Firma (Seguridad)
            event = Webhook.constructEvent(payload, sigHeader, endpointSecret);
        } catch (SignatureVerificationException e) {
            log.warn("⚠️ Firma de Webhook inválida.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid Signature");
        } catch (Exception e) {
            log.error("❌ Error parseando Webhook: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid Payload");
        }

        // 2. Despachar Evento
        StripeObject stripeObject = event.getDataObjectDeserializer().getObject().orElse(null);
        
        if (stripeObject == null) {
            return ResponseEntity.ok().build();
        }

        switch (event.getType()) {
            
            // ✅ CASO A: Pago de Cita o Suscripción Completado
            case "checkout.session.completed":
                handleCheckoutCompleted((Session) stripeObject);
                break;

            // ✅ CASO B: El Doctor terminó el Onboarding de su banco
            case "account.updated":
                handleAccountUpdated((com.stripe.model.Account) stripeObject);
                break;

            // ✅ CASO C: Pago recurrente exitoso (Renovación mensual)
            case "invoice.payment_succeeded":
                // Aquí actualizarías la fecha de expiración del plan del doctor
                log.info("💰 Factura pagada exitosamente.");
                break;

            // ❌ CASO D: Pago fallido (Tarjeta rechazada)
            case "invoice.payment_failed":
                // Aquí podrías mandar un email al doctor avisando
                log.warn("❌ Pago de factura fallido.");
                break;

            default:
                log.debug("Event ignored: {}", event.getType());
        }

        return ResponseEntity.ok("Received");
    }

    // --- Lógica de Negocio de los Eventos ---

    private void handleCheckoutCompleted(Session session) {
        String type = session.getMetadata().get("type");

        if ("APPOINTMENT_PAYMENT".equals(type)) {
            String appointmentId = session.getMetadata().get("appointment_id");
            log.info("✅ CITA PAGADA: ID #{}", appointmentId);
            
            // TODO: Actualizar estado de cita en BD (Directo o vía Pub/Sub)
            // appointmentRepository.markAsPaid(Long.valueOf(appointmentId));
            
        } else if ("SUBSCRIPTION_CHECKOUT".equals(type)) {
            String providerId = session.getMetadata().get("provider_id");
            log.info("✅ SUSCRIPCIÓN ACTIVADA: Provider #{}", providerId);
            
            // TODO: Actualizar plan del proveedor en BD
            // providerRepository.updatePlanStatus(Long.valueOf(providerId), PlanStatus.ACTIVE);
        }
    }

    private void handleAccountUpdated(com.stripe.model.Account account) {
        // Stripe nos avisa si el doctor ya puede cobrar (Charges Enabled)
        boolean payoutsEnabled = account.getPayoutsEnabled();
        boolean chargesEnabled = account.getChargesEnabled();
        
        merchantRepository.findByStripeAccountId(account.getId()).ifPresent(merchant -> {
            merchant.setPayoutsEnabled(payoutsEnabled);
            merchant.setChargesEnabled(chargesEnabled);
            // Si tiene ambos, el onboarding está completo
            merchant.setOnboardingCompleted(chargesEnabled && payoutsEnabled);
            merchantRepository.save(merchant);
            log.info("🏦 Estado de cuenta actualizado para Merchant {}: Payouts={}, Charges={}", 
                    account.getId(), payoutsEnabled, chargesEnabled);
        });
    }
    private void handleCheckoutCompleted(Session session) {
        String type = session.getMetadata().get("type");
        String paymentIntentId = session.getPaymentIntent(); // 👈 ESTO ES ORO

        if ("APPOINTMENT_PAYMENT".equals(type)) {
            String appointmentId = session.getMetadata().get("appointment_id");
            log.info("✅ CITA PAGADA: ID #{} | Transacción: {}", appointmentId, paymentIntentId);
            
            // AQUÍ ES DONDE DEBES GUARDARLO EN TU BD:
            // appointmentService.markAsPaid(Long.valueOf(appointmentId), paymentIntentId);
            
        } else if ("SUBSCRIPTION_CHECKOUT".equals(type)) {
            String providerId = session.getMetadata().get("provider_id");
            String subscriptionId = session.getSubscription(); // ID para cancelar a futuro
            
            log.info("✅ SUSCRIPCIÓN ACTIVADA: Provider #{} | Sub ID: {}", providerId, subscriptionId);
            
            // AQUÍ GUARDAS EL SUBSCRIPTION ID:
            // providerService.activatePlan(Long.valueOf(providerId), subscriptionId);
        }
    }

}
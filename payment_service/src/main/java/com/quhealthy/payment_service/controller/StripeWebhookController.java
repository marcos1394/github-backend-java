package com.quhealthy.payment_service.controller;

import com.quhealthy.payment_service.repository.MerchantAccountRepository;
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
    private String endpointSecret;

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
            event = Webhook.constructEvent(payload, sigHeader, endpointSecret);
        } catch (SignatureVerificationException e) {
            log.warn("⚠️ Firma de Webhook inválida.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid Signature");
        } catch (Exception e) {
            log.error("❌ Error parseando Webhook: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid Payload");
        }

        StripeObject stripeObject = event.getDataObjectDeserializer().getObject().orElse(null);
        
        if (stripeObject == null) {
            return ResponseEntity.ok().build();
        }

        switch (event.getType()) {
            case "checkout.session.completed":
                handleCheckoutCompleted((Session) stripeObject);
                break;

            case "account.updated":
                handleAccountUpdated((com.stripe.model.Account) stripeObject);
                break;

            case "invoice.payment_succeeded":
                log.info("💰 Factura pagada exitosamente.");
                break;

            case "invoice.payment_failed":
                log.warn("❌ Pago de factura fallido.");
                break;

            default:
                log.debug("Event ignored: {}", event.getType());
        }

        return ResponseEntity.ok("Received");
    }

    // --- MÉTODOS PRIVADOS ---

    private void handleCheckoutCompleted(Session session) {
        String type = session.getMetadata().get("type");
        String paymentIntentId = session.getPaymentIntent();

        if ("APPOINTMENT_PAYMENT".equals(type)) {
            String appointmentId = session.getMetadata().get("appointment_id");
            log.info("✅ CITA PAGADA: ID #{} | Transacción: {}", appointmentId, paymentIntentId);
            // Aquí llamarías a tu repositorio de citas para marcarla como pagada
            
        } else if ("SUBSCRIPTION_CHECKOUT".equals(type)) {
            String providerId = session.getMetadata().get("provider_id");
            String subscriptionId = session.getSubscription();
            log.info("✅ SUSCRIPCIÓN ACTIVADA: Provider #{} | Sub ID: {}", providerId, subscriptionId);
            // Aquí llamarías a tu repositorio de proveedores
        }
    }

    private void handleAccountUpdated(com.stripe.model.Account account) {
        boolean payoutsEnabled = account.getPayoutsEnabled();
        boolean chargesEnabled = account.getChargesEnabled();
        
        merchantRepository.findByStripeAccountId(account.getId()).ifPresent(merchant -> {
            merchant.setPayoutsEnabled(payoutsEnabled);
            merchant.setChargesEnabled(chargesEnabled);
            merchant.setOnboardingCompleted(chargesEnabled && payoutsEnabled);
            merchantRepository.save(merchant);
            log.info("🏦 Estado de cuenta actualizado para Merchant {}: Payouts={}, Charges={}", 
                    account.getId(), payoutsEnabled, chargesEnabled);
        });
    }
}
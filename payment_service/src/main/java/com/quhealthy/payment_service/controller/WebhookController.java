package com.quhealthy.payment_service.controller;

import com.quhealthy.payment_service.service.WebhookHandlerService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
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
public class WebhookController {

    @Value("${application.stripe.webhook-secret}")
    private String stripeWebhookSecret;

    private final WebhookHandlerService webhookHandlerService;

    @PostMapping("/stripe")
    public ResponseEntity<String> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader
    ) {
        // 1. Validar Firma Criptográfica (SEGURIDAD CRÍTICA)
        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, stripeWebhookSecret);
        } catch (SignatureVerificationException e) {
            log.error("⚠️ Firma de Webhook inválida. Posible ataque.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid Signature");
        } catch (Exception e) {
            log.error("❌ Error procesando webhook: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Webhook Error");
        }

        // 2. Enrutar según el tipo de evento
        try {
            switch (event.getType()) {
                case "invoice.payment_succeeded":
                    webhookHandlerService.handlePaymentSucceeded(event);
                    break;
                case "invoice.payment_failed":
                    webhookHandlerService.handlePaymentFailed(event);
                    break;
                case "customer.subscription.deleted":
                    webhookHandlerService.handleSubscriptionDeleted(event);
                    break;
                // Puedes agregar más casos (updated, trial_will_end, etc.)
                default:
                    log.debug("Evento ignorado: {}", event.getType());
            }
            return ResponseEntity.ok("Handled");
            
        } catch (Exception e) {
            log.error("❌ Error manejando lógica de negocio: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Handler Error");
        }
    }
}
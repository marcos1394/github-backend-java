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

    // Se inyecta desde application.properties
    @Value("${application.stripe.webhook-secret}")
    private String stripeWebhookSecret;

    private final WebhookHandlerService webhookHandlerService;

    @PostMapping("/stripe")
    public ResponseEntity<String> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader
    ) {
        // =================================================================
        // 1. VALIDACIÓN DE SEGURIDAD (CRÍTICO)
        // =================================================================
        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, stripeWebhookSecret);
        } catch (SignatureVerificationException e) {
            log.error("⚠️ ALERTA DE SEGURIDAD: Firma de Webhook inválida. Posible ataque.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid Signature");
        } catch (Exception e) {
            log.error("❌ Error técnico procesando payload de webhook: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Webhook Error");
        }

        // =================================================================
        // 2. ENRUTAMIENTO DE EVENTOS (Lógica de Negocio)
        // =================================================================
        try {
            log.debug("📩 Procesando evento Stripe: [Type: {}] [ID: {}]", event.getType(), event.getId());

            switch (event.getType()) {
                
                // 🆕 CREACIÓN DE SUSCRIPCIÓN (¡ESTO ES LO QUE FALTABA!)
                // Se dispara justo al terminar la compra. Crea el registro en BD.
                case "checkout.session.completed":
                    webhookHandlerService.handleCheckoutSessionCompleted(event);
                    break;

                // ✅ PAGO EXITOSO (Renovación o confirmación)
                case "invoice.payment_succeeded":
                    webhookHandlerService.handlePaymentSucceeded(event);
                    break;

                // ❌ PAGO FALLIDO
                case "invoice.payment_failed":
                    webhookHandlerService.handlePaymentFailed(event);
                    break;

                // 🔄 ACTUALIZACIÓN (Cambio de Plan, etc.)
                case "customer.subscription.updated":
                    webhookHandlerService.handleSubscriptionUpdated(event);
                    break;

                // 🗑️ ELIMINACIÓN
                case "customer.subscription.deleted":
                    webhookHandlerService.handleSubscriptionDeleted(event);
                    break;

                // --- EVENTOS SECUNDARIOS (Ignorar ruido) ---
                case "invoice.paid":
                case "invoice.finalized":
                case "invoice.created": // A veces llega antes, solo ruido
                    log.info("ℹ️ Evento informativo recibido y reconocido: {}", event.getType());
                    break;

                // ❓ DESCONOCIDO
                default:
                    log.debug("Event ignored (No handler defined): {}", event.getType());
            }

            return ResponseEntity.ok("Handled");

        } catch (Exception e) {
            log.error("❌ Error manejando lógica de negocio para evento {}: {}", event.getType(), e.getMessage(), e);
            // Retornamos 500 para que Stripe reintente enviar el webhook más tarde
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Handler Error");
        }
    }
}
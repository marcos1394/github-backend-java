package com.quhealthy.payment_service.controller;

import com.quhealthy.payment_service.service.MercadoPagoWebhookService;
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

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/payments/webhooks")
@RequiredArgsConstructor
public class WebhookController {

    // Inyección de dependencias de los servicios de negocio
    private final WebhookHandlerService stripeWebhookService;      // Tu servicio original de Stripe
    private final MercadoPagoWebhookService mercadoPagoWebhookService; // El nuevo servicio de MP

    // Se inyecta desde application.properties
    @Value("${application.stripe.webhook-secret}")
    private String stripeWebhookSecret;

    // =================================================================
    // 🟢 1. WEBHOOK STRIPE
    // URL: .../api/payments/webhooks/stripe
    // =================================================================
    @PostMapping("/stripe")
    public ResponseEntity<String> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader
    ) {
        // -------------------------------------------------------------
        // A. VALIDACIÓN DE SEGURIDAD (CRÍTICO)
        // -------------------------------------------------------------
        Event event;
        try {
            // Validamos criptográficamente que el evento venga de Stripe
            event = Webhook.constructEvent(payload, sigHeader, stripeWebhookSecret);
        } catch (SignatureVerificationException e) {
            log.error("⚠️ ALERTA DE SEGURIDAD: Firma de Webhook Stripe inválida. Posible ataque.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid Signature");
        } catch (Exception e) {
            log.error("❌ Error técnico procesando payload de Stripe: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Webhook Error");
        }

        // -------------------------------------------------------------
        // B. ENRUTAMIENTO DE EVENTOS (Lógica de Negocio)
        // -------------------------------------------------------------
        try {
            // log.debug("📩 Procesando evento Stripe: [Type: {}] [ID: {}]", event.getType(), event.getId());

            switch (event.getType()) {
                
                // 🆕 CREACIÓN DE SUSCRIPCIÓN
                case "checkout.session.completed":
                    stripeWebhookService.handleCheckoutSessionCompleted(event);
                    break;

                // ✅ PAGO EXITOSO (Renovación o confirmación)
                case "invoice.payment_succeeded":
                    stripeWebhookService.handlePaymentSucceeded(event);
                    break;

                // ❌ PAGO FALLIDO
                case "invoice.payment_failed":
                    stripeWebhookService.handlePaymentFailed(event);
                    break;

                // 🔄 ACTUALIZACIÓN (Cambio de Plan, etc.)
                case "customer.subscription.updated":
                    stripeWebhookService.handleSubscriptionUpdated(event);
                    break;

                // 🗑️ ELIMINACIÓN
                case "customer.subscription.deleted":
                    stripeWebhookService.handleSubscriptionDeleted(event);
                    break;

                // --- EVENTOS SECUNDARIOS (Ignorar ruido) ---
                case "invoice.paid":
                case "invoice.finalized":
                case "invoice.created": 
                    // log.info("ℹ️ Evento informativo Stripe reconocido: {}", event.getType());
                    break;

                // ❓ DESCONOCIDO
                default:
                    // log.debug("Stripe Event ignored (No handler defined): {}", event.getType());
            }

            return ResponseEntity.ok("Handled");

        } catch (Exception e) {
            log.error("❌ Error manejando lógica de negocio para evento Stripe {}: {}", event.getType(), e.getMessage(), e);
            // Retornamos 500 para que Stripe reintente enviar el webhook más tarde
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Handler Error");
        }
    }

    // =================================================================
    // 🔵 2. WEBHOOK MERCADOPAGO
    // URL: .../api/payments/webhooks/mercadopago
    // =================================================================
    /**
     * MercadoPago envía notificaciones JSON con la estructura básica:
     * { "type": "subscription_preapproval", "action": "updated", "data": { "id": "..." } }
     */
    @PostMapping("/mercadopago")
    public ResponseEntity<String> handleMercadoPagoWebhook(
            @RequestBody Map<String, Object> payload,
            @RequestParam(required = false) Map<String, String> queryParams
    ) {
        try {
            log.info("🔔 Webhook MercadoPago Recibido. Payload: {}", payload);
            
            // -------------------------------------------------------------
            // DELEGACIÓN AL SERVICIO
            // -------------------------------------------------------------
            // MercadoPago requiere una respuesta rápida (200 OK).
            // La validación de seguridad se hace DENTRO del servicio al consultar la API de MP.
            mercadoPagoWebhookService.processNotification(payload);

            return ResponseEntity.ok("OK");

        } catch (Exception e) {
            log.error("❌ Error crítico en Controller MP: {}", e.getMessage());
            // Devolvemos 500 para monitoreo, aunque MP seguirá reintentando.
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error processing webhook");
        }
    }
}
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

    // Se inyecta desde application.properties (que a su vez lee la variable de entorno de GCP)
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
        // Verificamos que la petición realmente venga de Stripe usando la firma criptográfica.
        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, stripeWebhookSecret);
        } catch (SignatureVerificationException e) {
            log.error("⚠️ ALERTA DE SEGURIDAD: Firma de Webhook inválida. Posible intento de ataque desde IP desconocida.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid Signature");
        } catch (Exception e) {
            log.error("❌ Error técnico procesando payload de webhook: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Webhook Error");
        }

        // =================================================================
        // 2. ENRUTAMIENTO DE EVENTOS (Lógica de Negocio)
        // =================================================================
        try {
            // Log para trazar qué evento llegó (útil en Cloud Run Logs)
            log.debug("📩 Procesando evento Stripe: [Type: {}] [ID: {}]", event.getType(), event.getId());

            switch (event.getType()) {
                
                // ✅ PAGO EXITOSO (Renovación o Primera Compra)
                case "invoice.payment_succeeded":
                    webhookHandlerService.handlePaymentSucceeded(event);
                    break;

                // ❌ PAGO FALLIDO (Tarjeta rechazada, fondos insuficientes)
                case "invoice.payment_failed":
                    webhookHandlerService.handlePaymentFailed(event);
                    break;

                // 🔄 ACTUALIZACIÓN DE SUSCRIPCIÓN (Cambio de Plan, Reactivación, Pause)
                // Este es el que faltaba y es vital para mantener el estado sincronizado.
                case "customer.subscription.updated":
                    webhookHandlerService.handleSubscriptionUpdated(event);
                    break;

                // 🗑️ SUSCRIPCIÓN ELIMINADA (Cancelación definitiva)
                case "customer.subscription.deleted":
                    webhookHandlerService.handleSubscriptionDeleted(event);
                    break;

                // --- EVENTOS SECUNDARIOS (Para limpiar logs) ---
                
                // 'invoice.paid' ocurre justo antes de 'payment_succeeded'. 
                // Lo reconocemos para no ver "Evento ignorado" en los logs, pero no duplicamos lógica.
                case "invoice.paid":
                case "invoice.finalized":
                case "checkout.session.completed":
                    log.info("ℹ️ Evento informativo recibido y reconocido: {}", event.getType());
                    break;

                // ❓ CUALQUIER OTRO EVENTO
                default:
                    log.debug("Event ignored (No handler defined): {}", event.getType());
            }

            return ResponseEntity.ok("Handled");

        } catch (Exception e) {
            // Capturamos cualquier error de lógica (NullPointer, DB exception) para no tirar el servidor
            // Stripe reintentará enviar el webhook más tarde si devolvemos 500.
            log.error("❌ Error manejando lógica de negocio para evento {}: {}", event.getType(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Handler Error");
        }
    }
}
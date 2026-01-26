package com.quhealthy.payment_service.service;

import com.quhealthy.payment_service.model.Subscription;
import com.quhealthy.payment_service.model.enums.SubscriptionStatus;
import com.quhealthy.payment_service.repository.SubscriptionRepository;
import com.stripe.model.Event;
import com.stripe.model.Invoice;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookHandlerService {

    private final SubscriptionRepository subscriptionRepository;

    /**
     * Maneja el evento invoice.payment_succeeded
     * (Pago exitoso: Renovación mensual o primera compra)
     */
    public void handlePaymentSucceeded(Event event) {
        Invoice invoice = (Invoice) event.getDataObjectDeserializer().getObject().orElse(null);
        if (invoice == null || invoice.getSubscription() == null) return;

        String stripeSubscriptionId = invoice.getSubscription();
        log.info("💰 Pago exitoso recibido para suscripción Stripe: {}", stripeSubscriptionId);

        updateSubscriptionStatus(stripeSubscriptionId, SubscriptionStatus.ACTIVE, invoice.getPeriodEnd());
    }

    /**
     * Maneja el evento invoice.payment_failed
     * (Tarjeta rechazada o fondos insuficientes)
     */
    public void handlePaymentFailed(Event event) {
        Invoice invoice = (Invoice) event.getDataObjectDeserializer().getObject().orElse(null);
        if (invoice == null || invoice.getSubscription() == null) return;

        String stripeSubscriptionId = invoice.getSubscription();
        log.warn("⛔ Pago fallido para suscripción Stripe: {}", stripeSubscriptionId);

        // No la cancelamos inmediatamente, la marcamos como "PAST_DUE" (Moroso)
        // para dar un periodo de gracia (Grace Period)
        updateSubscriptionStatus(stripeSubscriptionId, SubscriptionStatus.PAST_DUE, null);
    }

    /**
     * Maneja el evento customer.subscription.deleted
     * (El usuario canceló o se acabó el periodo de gracia)
     */
    public void handleSubscriptionDeleted(Event event) {
        com.stripe.model.Subscription stripeSub = (com.stripe.model.Subscription) event.getDataObjectDeserializer().getObject().orElse(null);
        if (stripeSub == null) return;

        log.info("🗑️ Suscripción eliminada/cancelada en Stripe: {}", stripeSub.getId());
        
        updateSubscriptionStatus(stripeSub.getId(), SubscriptionStatus.CANCELED, null);
    }

    // --- Helper Privado ---
    private void updateSubscriptionStatus(String stripeSubscriptionId, SubscriptionStatus status, Long periodEndTimestamp) {
        Optional<Subscription> subOpt = subscriptionRepository.findByExternalSubscriptionId(stripeSubscriptionId);

        if (subOpt.isPresent()) {
            Subscription sub = subOpt.get();
            sub.setStatus(status);
            
            // Si viene una fecha de fin de periodo (renovación), actualizamos
            if (periodEndTimestamp != null) {
                LocalDateTime endDate = LocalDateTime.ofInstant(Instant.ofEpochSecond(periodEndTimestamp), ZoneId.systemDefault());
                sub.setCurrentPeriodEnd(endDate);
            }

            subscriptionRepository.save(sub);
            log.info("✅ Base de datos actualizada: Sub ID {} -> Estado {}", sub.getId(), status);
        } else {
            log.error("⚠️ Recibido evento para suscripción {} que NO existe en nuestra BD local.", stripeSubscriptionId);
        }
    }
}
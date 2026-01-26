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

        // Al pagar, la suscripción siempre pasa a ACTIVE
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

        // La marcamos como PAST_DUE para permitir un periodo de gracia (Dunning)
        // El frontend debería mostrar un aviso: "Actualiza tu pago"
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

    /**
     * 🔥 NUEVO: Maneja el evento customer.subscription.updated
     * Vital para cambios de plan, reactivaciones o periodos de prueba.
     */
    public void handleSubscriptionUpdated(Event event) {
        com.stripe.model.Subscription stripeSub = (com.stripe.model.Subscription) event.getDataObjectDeserializer().getObject().orElse(null);
        if (stripeSub == null) return;

        log.info("🔄 Actualización de suscripción recibida: {} -> Estado Stripe: {}", stripeSub.getId(), stripeSub.getStatus());

        SubscriptionStatus status = mapStripeStatusToLocal(stripeSub.getStatus());

        // Si el estado es desconocido (ej: incomplete_expired), no hacemos nada o lo manejamos como cancelado
        if (status != null) {
            updateSubscriptionStatus(stripeSub.getId(), status, stripeSub.getCurrentPeriodEnd());
        }
    }

    // --- Helpers Privados ---

    private void updateSubscriptionStatus(String stripeSubscriptionId, SubscriptionStatus status, Long periodEndTimestamp) {
        Optional<Subscription> subOpt = subscriptionRepository.findByExternalSubscriptionId(stripeSubscriptionId);

        if (subOpt.isPresent()) {
            Subscription sub = subOpt.get();
            
            // Solo actualizamos si el estado es diferente o si se extendió la fecha
            if (sub.getStatus() != status || periodEndTimestamp != null) {
                sub.setStatus(status);
                
                // Si viene fecha de renovación, la actualizamos
                if (periodEndTimestamp != null) {
                    LocalDateTime endDate = LocalDateTime.ofInstant(Instant.ofEpochSecond(periodEndTimestamp), ZoneId.systemDefault());
                    sub.setCurrentPeriodEnd(endDate);
                }

                subscriptionRepository.save(sub);
                log.info("✅ BD Actualizada: Sub ID {} -> {}", sub.getId(), status);
            }
        } else {
            // Nota: Es normal que esto pase en la creación inicial (checkout.session.completed)
            // antes de que nuestra BD sepa de la suscripción.
            log.warn("⚠️ Webhook recibido para suscripción desconocida: {}", stripeSubscriptionId);
        }
    }

    private SubscriptionStatus mapStripeStatusToLocal(String stripeStatus) {
        switch (stripeStatus) {
            case "active": return SubscriptionStatus.ACTIVE;
            case "past_due": return SubscriptionStatus.PAST_DUE;
            case "canceled": return SubscriptionStatus.CANCELED;
            case "trialing": return SubscriptionStatus.TRIALING;
            case "unpaid": return SubscriptionStatus.PAST_DUE; 
            case "incomplete": return SubscriptionStatus.PENDING; // Esperando primer pago
            default: return null; // Ignorar otros estados
        }
    }
}
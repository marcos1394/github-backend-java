package com.quhealthy.payment_service.service;

import com.quhealthy.payment_service.model.Subscription;
import com.quhealthy.payment_service.model.enums.PaymentGateway;
import com.quhealthy.payment_service.model.enums.SubscriptionStatus;
import com.quhealthy.payment_service.repository.SubscriptionRepository;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.Invoice;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
     * ✅ CRÍTICO: CREACIÓN DE LA SUSCRIPCIÓN
     * Se dispara cuando el usuario completa el pago en el Checkout de Stripe.
     * Aquí es donde nacen los registros en nuestra Base de Datos.
     */
    @Transactional
    public void handleCheckoutSessionCompleted(Event event) {
        // 🛠️ FIX DE ROBUSTEZ: Usamos el helper 'deserialize' para evitar errores por versión de API
        Session session = (Session) deserialize(event);
        
        if (session == null) {
            log.error("❌ ERROR CRÍTICO: No se pudo deserializar la Sesión del evento {}. El JSON no es interpretable.", event.getId());
            return;
        }

        // Recuperamos el ID del Doctor (Provider) que enviamos al crear la sesión
        String clientReferenceId = session.getClientReferenceId();
        
        if (clientReferenceId == null) {
            log.error("⚠️ ALERTA: Checkout completado sin ClientReferenceId. No podemos vincular el pago a ningún usuario.");
            return;
        }

        Long providerId;
        try {
            providerId = Long.parseLong(clientReferenceId);
        } catch (NumberFormatException e) {
            log.error("❌ Error parseando providerId: {}", clientReferenceId);
            return;
        }

        String stripeCustomerId = session.getCustomer();
        String stripeSubscriptionId = session.getSubscription();

        log.info("✨ Checkout Completado. Creando nueva suscripción para Provider: {}", providerId);

        // Verificar si ya existe para evitar duplicados (Idempotencia básica)
        Optional<Subscription> existing = subscriptionRepository.findByExternalSubscriptionId(stripeSubscriptionId);
        if (existing.isPresent()) {
            log.info("ℹ️ La suscripción {} ya existe en BD. Saltando creación.", stripeSubscriptionId);
            return;
        }

        // Crear la entidad
        Subscription subscription = new Subscription();
        subscription.setProviderId(providerId);
        subscription.setExternalCustomerId(stripeCustomerId);
        subscription.setExternalSubscriptionId(stripeSubscriptionId);
        subscription.setGateway(PaymentGateway.STRIPE);
        
        // Estado inicial: ACTIVE
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        
        // Fechas de auditoría
        LocalDateTime now = LocalDateTime.now();
        subscription.setCreatedAt(now);
        subscription.setUpdatedAt(now);
        
        // Fechas del periodo (Inicializamos con 'ahora', se corregirán con el evento invoice.payment_succeeded)
        subscription.setCurrentPeriodStart(now);
        subscription.setCurrentPeriodEnd(now.plusMonths(1)); 

        subscriptionRepository.save(subscription);
        log.info("✅ Suscripción creada exitosamente en BD: Provider {} -> Sub ID {}", providerId, stripeSubscriptionId);
    }

    /**
     * 💰 PAGO EXITOSO (Renovación mensual o confirmación de primera compra)
     * Extiende la fecha de vencimiento.
     */
    @Transactional
    public void handlePaymentSucceeded(Event event) {
        Invoice invoice = (Invoice) deserialize(event);
        if (invoice == null || invoice.getSubscription() == null) return;

        String stripeSubscriptionId = invoice.getSubscription();
        log.info("💰 Pago exitoso recibido para suscripción Stripe: {}", stripeSubscriptionId);

        // Al pagar, la suscripción se confirma como ACTIVE y actualizamos la fecha fin
        updateSubscriptionStatus(stripeSubscriptionId, SubscriptionStatus.ACTIVE, invoice.getPeriodEnd());
    }

    /**
     * ⛔ PAGO FALLIDO
     * Tarjeta rechazada, fondos insuficientes o expirada.
     */
    @Transactional
    public void handlePaymentFailed(Event event) {
        Invoice invoice = (Invoice) deserialize(event);
        if (invoice == null || invoice.getSubscription() == null) return;

        String stripeSubscriptionId = invoice.getSubscription();
        log.warn("⛔ Pago fallido para suscripción Stripe: {}", stripeSubscriptionId);

        // Marcamos como PAST_DUE (Moroso). El usuario sigue teniendo acceso (Grace Period)
        updateSubscriptionStatus(stripeSubscriptionId, SubscriptionStatus.PAST_DUE, null);
    }

    /**
     * 🗑️ SUSCRIPCIÓN ELIMINADA
     * Cancelación manual o impago definitivo.
     */
    @Transactional
    public void handleSubscriptionDeleted(Event event) {
        com.stripe.model.Subscription stripeSub = (com.stripe.model.Subscription) deserialize(event);
        if (stripeSub == null) return;

        log.info("🗑️ Suscripción eliminada/cancelada en Stripe: {}", stripeSub.getId());
        
        updateSubscriptionStatus(stripeSub.getId(), SubscriptionStatus.CANCELED, null);
    }

    /**
     * 🔄 ACTUALIZACIÓN DE ESTADO (Cambio de Plan, Reactivación)
     * Vital para mantener sincronía cuando el usuario usa el Portal de Cliente.
     */
    @Transactional
    public void handleSubscriptionUpdated(Event event) {
        com.stripe.model.Subscription stripeSub = (com.stripe.model.Subscription) deserialize(event);
        if (stripeSub == null) return;

        log.info("🔄 Actualización de suscripción recibida: {} -> Estado Stripe: {}", stripeSub.getId(), stripeSub.getStatus());

        SubscriptionStatus status = mapStripeStatusToLocal(stripeSub.getStatus());

        if (status != null) {
            updateSubscriptionStatus(stripeSub.getId(), status, stripeSub.getCurrentPeriodEnd());
        }
    }

    // =================================================================
    // 🛠️ HELPER DE DESERIALIZACIÓN (EL SALVAVIDAS) 🛟
    // =================================================================
    /**
     * Este método es vital. La librería de Stripe Java a veces falla al leer JSONs
     * generados por una versión de API más nueva (mismatch).
     * Este helper intenta leerlo de forma segura, y si falla, fuerza la lectura (unsafe).
     */
    private StripeObject deserialize(Event event) {
        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
        
        if (deserializer.getObject().isPresent()) {
            return deserializer.getObject().get();
        } else {
            // Si llegamos aquí, es porque hay campos nuevos en el JSON que Java no conoce.
            // Usamos deserializeUnsafe() para forzar la lectura del objeto de todas formas.
            log.warn("⚠️ Advertencia de Versión Stripe: Usando deserializeUnsafe() para evento {}", event.getType());
            return deserializer.deserializeUnsafe();
        }
    }

    // =================================================================
    // 🛠️ OTROS HELPERS PRIVADOS
    // =================================================================

    private void updateSubscriptionStatus(String stripeSubscriptionId, SubscriptionStatus status, Long periodEndTimestamp) {
        Optional<Subscription> subOpt = subscriptionRepository.findByExternalSubscriptionId(stripeSubscriptionId);

        if (subOpt.isPresent()) {
            Subscription sub = subOpt.get();
            boolean changed = false;

            // Actualizar estado si cambió
            if (sub.getStatus() != status) {
                sub.setStatus(status);
                changed = true;
            }
            
            // Actualizar fecha fin si Stripe envía una nueva (Renovación)
            if (periodEndTimestamp != null) {
                LocalDateTime newEndDate = LocalDateTime.ofInstant(Instant.ofEpochSecond(periodEndTimestamp), ZoneId.systemDefault());
                if (!newEndDate.equals(sub.getCurrentPeriodEnd())) {
                    sub.setCurrentPeriodEnd(newEndDate);
                    changed = true;
                }
            }

            if (changed) {
                sub.setUpdatedAt(LocalDateTime.now());
                subscriptionRepository.save(sub);
                log.info("✅ BD Actualizada: Sub ID {} -> Estado: {} | Fin: {}", sub.getId(), status, sub.getCurrentPeriodEnd());
            }
        } else {
            // NOTA: Si llega aquí en 'payment_succeeded' antes que 'checkout.completed' (Race Condition),
            // es normal ver este warning. El evento de checkout llegará milisegundos después y creará el registro.
            log.warn("⚠️ Webhook recibido para suscripción {} que aun no existe en BD local.", stripeSubscriptionId);
        }
    }

    private SubscriptionStatus mapStripeStatusToLocal(String stripeStatus) {
        if (stripeStatus == null) return null;
        switch (stripeStatus) {
            case "active": return SubscriptionStatus.ACTIVE;
            case "past_due": return SubscriptionStatus.PAST_DUE;
            case "canceled": return SubscriptionStatus.CANCELED;
            case "trialing": return SubscriptionStatus.TRIALING;
            case "unpaid": return SubscriptionStatus.PAST_DUE; 
            case "incomplete": return SubscriptionStatus.PENDING;
            case "incomplete_expired": return SubscriptionStatus.CANCELED;
            case "paused": return SubscriptionStatus.PAST_DUE; // O crear un estado PAUSED si lo tienes
            default: return null;
        }
    }
}
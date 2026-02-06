package com.quhealthy.payment_service.service;

import com.quhealthy.payment_service.model.Subscription;
import com.quhealthy.payment_service.model.enums.PaymentGateway;
import com.quhealthy.payment_service.model.enums.SubscriptionStatus;
import com.quhealthy.payment_service.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final StripeService stripeService;

    // ID del precio gratuito en Stripe (Definido en dashboard de Stripe)
    // Ej: price_1Qxyz...
    @Value("${application.stripe.free-plan-id}")
    private String stripeFreePriceId;

    /**
     * Crea la suscripción inicial sincronizada con Stripe.
     */
    @Transactional
    public void createInitialFreeSubscription(Long providerId, String email, String name) {

        // 1. Idempotencia
        if (subscriptionRepository.existsByProviderId(providerId)) {
            log.warn("⚠️ Proveedor {} ya tiene suscripción. Ignorando evento.", providerId);
            return;
        }

        try {
            log.info("🔄 Sincronizando nuevo proveedor {} con Stripe...", providerId);

            // 2. Crear Customer en Stripe
            var stripeCustomer = stripeService.createCustomer(email, name, providerId);

            // 3. Crear Suscripción Gratuita en Stripe
            var stripeSub = stripeService.createBackendSubscription(stripeCustomer.getId(), stripeFreePriceId);

            // 4. Convertir Fechas (Stripe usa Unix Timestamp -> Java LocalDateTime)
            LocalDateTime start = LocalDateTime.ofInstant(Instant.ofEpochSecond(stripeSub.getCurrentPeriodStart()), ZoneId.of("UTC"));
            LocalDateTime end = LocalDateTime.ofInstant(Instant.ofEpochSecond(stripeSub.getCurrentPeriodEnd()), ZoneId.of("UTC"));

            // 5. Guardar en PostgreSQL
            Subscription subscription = Subscription.builder()
                    .providerId(providerId)
                    .planId("5") // ID Interno del Plan Gratuito

                    // Nota: Usamos STRIPE como gateway porque la suscripción VIVE en Stripe,
                    // aunque sea gratuita. Así mantenemos el ID real 'sub_...'
                    .gateway(PaymentGateway.STRIPE)
                    .status(SubscriptionStatus.ACTIVE)

                    // IDs Reales de Stripe
                    .externalSubscriptionId(stripeSub.getId())
                    .externalCustomerId(stripeCustomer.getId())

                    .currentPeriodStart(start)
                    .currentPeriodEnd(end)
                    .build();

            subscriptionRepository.save(subscription);
            log.info("✅ Plan Gratuito activado y sincronizado: [Sub: {}] [Cus: {}]", stripeSub.getId(), stripeCustomer.getId());

        } catch (Exception e) {
            log.error("❌ Falló la creación del plan gratuito: {}", e.getMessage());
            // Aquí podrías implementar un mecanismo de reintento (Dead Letter Queue)
        }
    }
}
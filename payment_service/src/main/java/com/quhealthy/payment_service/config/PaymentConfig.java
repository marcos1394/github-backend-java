package com.quhealthy.payment_service.config;

import com.mercadopago.MercadoPagoConfig;
import com.stripe.Stripe;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class PaymentConfig {

    // Coincide con: stripe.api-key=${STRIPE_API_KEY} en tu properties
    @Value("${stripe.api-key}")
    private String stripeApiKey;

    // 👇 CORRECCIÓN: Ahora coincide con application.mercadopago.access-token
    @Value("${application.mercadopago.access-token}")
    private String mpAccessToken;

    /**
     * Este método se ejecuta automáticamente cuando Spring arranca.
     * Configura los SDKs estáticos de Stripe y MercadoPago.
     */
    @PostConstruct
    public void init() {
        // 1. Inicializar Stripe
        if (stripeApiKey != null && !stripeApiKey.isBlank()) {
            Stripe.apiKey = stripeApiKey;
            log.info("✅ Stripe SDK inicializado correctamente.");
        } else {
            log.warn("⚠️ No se encontró STRIPE_API_KEY. Los pagos con Stripe fallarán.");
        }

        // 2. Inicializar MercadoPago
        if (mpAccessToken != null && !mpAccessToken.isBlank()) {
            MercadoPagoConfig.setAccessToken(mpAccessToken);
            log.info("✅ MercadoPago SDK inicializado correctamente.");
        } else {
            log.warn("⚠️ No se encontró MP_ACCESS_TOKEN. Los pagos con MercadoPago fallarán.");
        }
    }
}
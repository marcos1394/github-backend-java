package com.quhealthy.payment_service.service;

import com.stripe.exception.*;
import com.stripe.model.Invoice;
// Importamos solo la Session de Checkout
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.SubscriptionResumeParams;
import com.stripe.param.SubscriptionUpdateParams;
// Importamos solo los Params de Checkout
import com.stripe.param.checkout.SessionCreateParams;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class StripeService {

    /**
     * Crea una sesión de Checkout de Nivel Enterprise.
     * Incluye:
     * 1. Gestión Fiscal Automática (Tax).
     * 2. Idempotencia (Evita cobros dobles).
     * 3. Soporte para Periodos de Prueba (Trials).
     * 4. Metadata para Webhooks.
     */
    public Session createSubscriptionCheckout(Long providerId, String userEmail, String priceId, 
                                              String successUrl, String cancelUrl, 
                                              String existingCustomerId, Integer trialDays) {
        
        // 1. Idempotencia: Generamos una clave única para esta transacción
        String idempotencyKey = UUID.randomUUID().toString();
        RequestOptions options = RequestOptions.builder()
                .setIdempotencyKey(idempotencyKey)
                .build();

        log.info("🔵 Iniciando Checkout [Provider: {}] [Tax: AUTO] [Trial: {} dias]", providerId, trialDays);

        try {
            // 2. Metadata: Datos que viajarán a Stripe y volverán en el Webhook
            Map<String, String> metadata = new HashMap<>();
            metadata.put("provider_id", String.valueOf(providerId));
            metadata.put("plan_id", priceId);
            metadata.put("source", "quhealthy_platform");

            // 3. Construcción de Parámetros de la Sesión
            SessionCreateParams.Builder paramsBuilder = SessionCreateParams.builder()
                    // Modo Suscripción (Recurrente)
                    .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                    
                    // URLs de retorno
                    .setSuccessUrl(successUrl)
                    .setCancelUrl(cancelUrl)

                    .setClientReferenceId(String.valueOf(providerId))
                    
                    // Permitir que el usuario ponga cupones de descuento si tiene uno
                    .setAllowPromotionCodes(true) 
                    
                    // ============================================================
                    // 🌍 PUNTO 1: GESTIÓN FISCAL AUTOMÁTICA (STRIPE TAX)
                    // ============================================================
                    .setAutomaticTax(
                            SessionCreateParams.AutomaticTax.builder()
                                    .setEnabled(true) 
                                    .build()
                    )
                    // Obligamos a recolectar la dirección del cliente para saber qué impuesto aplicar
                    .setBillingAddressCollection(SessionCreateParams.BillingAddressCollection.AUTO)
                    // ============================================================

                    // Inyectamos la metadata a la sesión
                    .putAllMetadata(metadata)
                    
                    // Configuración específica de la suscripción
                    .setSubscriptionData(
                            SessionCreateParams.SubscriptionData.builder()
                                    .putAllMetadata(metadata) 
                                    // ⏳ PUNTO 2: PERIODOS DE PRUEBA (TRIAL)
                                    .setTrialPeriodDays(trialDays != null && trialDays > 0 ? Long.valueOf(trialDays) : null)
                                    .build()
                    )
                    
                    // El Producto a comprar (El Plan)
                    .addLineItem(
                            SessionCreateParams.LineItem.builder()
                                    .setQuantity(1L)
                                    .setPrice(priceId)
                                    .build()
                    );

            // 4. Gestión de Cliente (Nuevo vs Recurrente)
            if (existingCustomerId != null && !existingCustomerId.isBlank()) {
                log.debug("🔄 Cliente recurrente detectado: {}", existingCustomerId);
                paramsBuilder.setCustomer(existingCustomerId);
                
                // Si es cliente viejo, permitimos que actualice su dirección
                paramsBuilder.setCustomerUpdate(
                        SessionCreateParams.CustomerUpdate.builder()
                                .setAddress(SessionCreateParams.CustomerUpdate.Address.AUTO)
                                .build()
                );
            } else {
                log.debug("✨ Nuevo cliente: {}", userEmail);
                paramsBuilder.setCustomerEmail(userEmail);
                // La colección de dirección ya está seteada arriba en .setBillingAddressCollection
            }

            // 5. Llamada final a Stripe
            Session session = Session.create(paramsBuilder.build(), options);
            
            log.info("✅ Sesión creada exitosamente: {}", session.getId());
            return session;

        } catch (StripeException e) {
            log.error("❌ Error Crítico en Stripe Checkout: {}", e.getMessage(), e);
            throw new RuntimeException("Error al iniciar el pago: " + e.getMessage());
        }
    }

    /**
     * Crea una sesión del Portal de Clientes (Billing Portal).
     * NOTA: Aquí usamos los nombres completos (com.stripe.param.billingportal...)
     * para evitar conflicto con las clases de Checkout.
     */
    public String createCustomerPortalSession(String externalCustomerId, String returnUrl) {
        if (externalCustomerId == null) {
            throw new IllegalArgumentException("No se puede abrir el portal sin un Customer ID");
        }

        try {
            // Usamos ruta completa para diferenciarlo de SessionCreateParams de Checkout
            com.stripe.param.billingportal.SessionCreateParams params = 
                com.stripe.param.billingportal.SessionCreateParams.builder()
                    .setCustomer(externalCustomerId)
                    .setReturnUrl(returnUrl)
                    .build();

            // Usamos ruta completa para diferenciarlo de Session de Checkout
            com.stripe.model.billingportal.Session portalSession = 
                com.stripe.model.billingportal.Session.create(params);
            
            return portalSession.getUrl();

        } catch (StripeException e) {
            log.error("❌ Error creando Portal Session: {}", e.getMessage());
            throw new RuntimeException("No se pudo acceder al portal de facturación.");
        }
    }

    /**
     * Cancela una suscripción inmediatamente (Hard Cancel).
     * Útil para fraudes o peticiones de admin.
     */
    public void cancelSubscriptionImmediately(String subscriptionId) {
        try {
            com.stripe.model.Subscription subscription = com.stripe.model.Subscription.retrieve(subscriptionId);
            subscription.cancel();
            log.info("🛑 Suscripción {} cancelada manualmente (inmediato).", subscriptionId);
        } catch (StripeException e) {
            log.error("❌ Error cancelando suscripción {}: {}", subscriptionId, e.getMessage());
            throw new RuntimeException("Error al cancelar la suscripción en Stripe.");
        }
    }

    /**
     * Cambia el plan (Upgrade/Downgrade) con prorrateo.
     */
    public void changeSubscriptionPlan(String subscriptionId, String newPriceId) {
        try {
            com.stripe.model.Subscription subscription = com.stripe.model.Subscription.retrieve(subscriptionId);
            // Obtenemos el ID del ítem actual para actualizarlo
            String subscriptionItemId = subscription.getItems().getData().get(0).getId();

            SubscriptionUpdateParams params = SubscriptionUpdateParams.builder()
                    .setProrationBehavior(SubscriptionUpdateParams.ProrationBehavior.CREATE_PRORATIONS)
                    .addItem(
                            SubscriptionUpdateParams.Item.builder()
                                    .setId(subscriptionItemId)
                                    .setPrice(newPriceId)
                                    .build()
                    )
                    .build();

            subscription.update(params);
            log.info("⬆️⬇️ Suscripción {} actualizada al plan {}", subscriptionId, newPriceId);

        } catch (StripeException e) {
            log.error("❌ Error cambiando plan Stripe: {}", e.getMessage());
            throw new RuntimeException("Error al cambiar el plan de suscripción.");
        }
    }

    // ==========================================
    // 🚀 FUNCIONALIDADES ENTERPRISE
    // ==========================================

    /**
     * Cancela la suscripción AL FINAL del periodo (Soft Cancel).
     * El usuario mantiene acceso hasta que se acabe el mes pagado.
     */
    public void cancelSubscriptionAtPeriodEnd(String subscriptionId) {
        try {
            com.stripe.model.Subscription subscription = com.stripe.model.Subscription.retrieve(subscriptionId);
            
            SubscriptionUpdateParams params = SubscriptionUpdateParams.builder()
                    .setCancelAtPeriodEnd(true) // 👈 La clave para no cortar el servicio hoy
                    .build();

            subscription.update(params);
            log.info("⏳ Suscripción {} marcada para cancelar a fin de mes.", subscriptionId);
        } catch (StripeException e) {
            log.error("❌ Error programando cancelación suave: {}", e.getMessage());
            throw new RuntimeException("Error al programar cancelación.");
        }
    }

    /**
     * Reactiva una suscripción que estaba marcada para cancelar a fin de mes.
     * Útil para clientes que se arrepienten antes de perder el acceso.
     */
    public void resumeSubscription(String subscriptionId) {
        try {
            com.stripe.model.Subscription subscription = com.stripe.model.Subscription.retrieve(subscriptionId);
            
            // Verificamos si realmente se puede resumir
            if (Boolean.TRUE.equals(subscription.getCancelAtPeriodEnd())) {
                SubscriptionResumeParams params = SubscriptionResumeParams.builder()
                        .setBillingCycleAnchor(SubscriptionResumeParams.BillingCycleAnchor.UNCHANGED) // Mantiene la fecha de cobro original
                        .build();
                
                subscription.resume(params);
                log.info("♻️ Suscripción {} reactivada exitosamente.", subscriptionId);
            } else {
                log.warn("⚠️ Intento de reactivar suscripción {} que no estaba cancelada.", subscriptionId);
            }
        } catch (StripeException e) {
            log.error("❌ Error reactivando suscripción: {}", e.getMessage());
            throw new RuntimeException("Error al reactivar la suscripción.");
        }
    }

    /**
     * Obtiene la URL pública del PDF de la última factura generada.
     * Útil para el dashboard del proveedor.
     */
    public String getLatestInvoiceUrl(String subscriptionId) {
        try {
            com.stripe.model.Subscription subscription = com.stripe.model.Subscription.retrieve(subscriptionId);
            
            String latestInvoiceId = subscription.getLatestInvoice();
            if (latestInvoiceId == null) return null;

            Invoice invoice = Invoice.retrieve(latestInvoiceId);
            return invoice.getInvoicePdf(); 
            
        } catch (StripeException e) {
            log.error("❌ Error obteniendo factura: {}", e.getMessage());
            return null; // No rompemos el flujo si falla esto
        }
    }

    /**
     * Sincronización Manual: Consulta el estado real en Stripe.
     * Vital para recuperar suscripciones desincronizadas si fallaron los webhooks.
     */
    public com.stripe.model.Subscription retrieveSubscription(String subscriptionId) {
        try {
            return com.stripe.model.Subscription.retrieve(subscriptionId);
        } catch (StripeException e) {
            log.error("❌ Error recuperando suscripción de Stripe: {}", e.getMessage());
            throw new RuntimeException("No se pudo sincronizar con Stripe.");
        }
    }
}
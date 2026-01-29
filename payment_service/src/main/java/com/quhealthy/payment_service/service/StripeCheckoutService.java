package com.quhealthy.payment_service.service;

import com.stripe.exception.StripeException;
import com.stripe.model.Invoice;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.SubscriptionResumeParams;
import com.stripe.param.SubscriptionUpdateParams;
import com.stripe.param.checkout.SessionCreateParams;
import com.stripe.model.Refund;
import com.stripe.param.RefundCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StripeCheckoutService {

    private final StripeIdentityService identityService;
    private final FeeCalculatorService feeCalculatorService;

    @Value("${application.frontend.url}")
    private String frontendUrl;

    // =================================================================
    // 🩺 1. COBRO DE CITAS (MARKETPLACE SPLIT PAYMENT)
    // El Paciente paga -> Nosotros cobramos comisión -> Doctor recibe el resto
    // =================================================================

    public Session createAppointmentCheckout(
            Long appointmentId,
            Long patientId, String patientEmail, String patientName,
            Long providerId, Long providerPlanId, // Recibimos el ID numérico del plan
            BigDecimal totalAmount, String currency) {

        log.info("🩺 Iniciando Checkout Cita #{}", appointmentId);

        // 1. Obtener Identidades (Stripe IDs)
        // Si el paciente no existe en Stripe, se crea al vuelo.
        String customerId = identityService.getOrCreateCustomer(patientId, patientEmail, patientName);
        
        // Si el doctor no tiene cuenta bancaria configurada, esto lanzará error (IdentityService valida).
        String merchantId = identityService.getMerchantAccountId(providerId);

        // 2. Calcular nuestra comisión (Application Fee)
        BigDecimal platformFee = feeCalculatorService.calculatePlatformFee(totalAmount, providerPlanId);

        // 3. Convertir a Centavos (Stripe maneja enteros: $100.00 -> 10000)
        long amountCents = totalAmount.multiply(BigDecimal.valueOf(100)).longValue();
        long feeCents = platformFee.multiply(BigDecimal.valueOf(100)).longValue();

        // 4. Crear la Sesión de Pago
        try {
            SessionCreateParams params = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.PAYMENT) // Pago único
                    .setSuccessUrl(frontendUrl + "/appointments/" + appointmentId + "/success")
                    .setCancelUrl(frontendUrl + "/appointments/" + appointmentId + "/cancel")
                    .setCustomer(customerId) // El paciente paga
                    .setClientReferenceId(String.valueOf(appointmentId))
                    
                    // Metadata para conciliación
                    .putMetadata("appointment_id", String.valueOf(appointmentId))
                    .putMetadata("provider_id", String.valueOf(providerId))
                    .putMetadata("type", "APPOINTMENT_PAYMENT")
                    
                    // 🔥 LA MAGIA: Direct Charge con Application Fee
                    .setPaymentIntentData(SessionCreateParams.PaymentIntentData.builder()
                            .setApplicationFeeAmount(feeCents) // Lo que nos quedamos nosotros
                            .setTransferData(SessionCreateParams.PaymentIntentData.TransferData.builder()
                                    .setDestination(merchantId) // El resto va directo a la cuenta Express del Doctor
                                    .build())
                            .build())
                            
                    // El Producto que ve el paciente en el recibo
                    .addLineItem(SessionCreateParams.LineItem.builder()
                            .setQuantity(1L)
                            .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                    .setCurrency(currency)
                                    .setUnitAmount(amountCents) // Monto total cobrado a la tarjeta
                                    .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                            .setName("Consulta Médica")
                                            .setDescription("Cita ID: " + appointmentId)
                                            .build())
                                    .build())
                            .build())
                    .build();

            return Session.create(params);

        } catch (StripeException e) {
            log.error("❌ Error creando Checkout de Cita: {}", e.getMessage());
            throw new RuntimeException("Error al procesar el pago de la cita", e);
        }
    }

    // =================================================================
    // 🛒 2. COBRO DE SUSCRIPCIONES (SAAS)
    // El Doctor paga su mensualidad -> Nosotros cobramos el 100%
    // =================================================================

    public Session createSubscriptionCheckout(
            Long providerId, String userEmail, String userName, // Agregamos nombre para crear Customer
            String priceId, 
            String successUrl, String cancelUrl, 
            Integer trialDays) {
        
        // Idempotencia para evitar cobros dobles si le dan click mil veces
        String idempotencyKey = UUID.randomUUID().toString();
        RequestOptions options = RequestOptions.builder().setIdempotencyKey(idempotencyKey).build();

        log.info("🔵 Iniciando Checkout Suscripción [Provider: {}] [Trial: {} dias]", providerId, trialDays);

        // 1. Obtener Identidad de Pagador del Doctor
        // Aquí el Doctor actúa como "Customer" (comprador de nuestro software)
        String customerId = identityService.getOrCreateCustomer(providerId, userEmail, userName);

        try {
            Map<String, String> metadata = new HashMap<>();
            metadata.put("provider_id", String.valueOf(providerId));
            metadata.put("plan_id", priceId);
            metadata.put("type", "SUBSCRIPTION_CHECKOUT");

            SessionCreateParams params = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.SUBSCRIPTION) // Recurrente
                    .setSuccessUrl(successUrl)
                    .setCancelUrl(cancelUrl)
                    .setClientReferenceId(String.valueOf(providerId))
                    .setCustomer(customerId) // Usamos el ID recuperado
                    
                    // Configuración Fiscal Automática
                    .setAutomaticTax(SessionCreateParams.AutomaticTax.builder().setEnabled(true).build())
                    .setBillingAddressCollection(SessionCreateParams.BillingAddressCollection.AUTO)
                    .setAllowPromotionCodes(true)
                    
                    .putAllMetadata(metadata)
                    .setSubscriptionData(SessionCreateParams.SubscriptionData.builder()
                            .putAllMetadata(metadata)
                            .setTrialPeriodDays(trialDays != null && trialDays > 0 ? Long.valueOf(trialDays) : null)
                            .build())
                    .addLineItem(SessionCreateParams.LineItem.builder()
                            .setQuantity(1L)
                            .setPrice(priceId) // ID del precio en Stripe (ej: price_1Rn...)
                            .build())
                    .build();

            return Session.create(params, options);

        } catch (StripeException e) {
            log.error("❌ Error en Subscription Checkout: {}", e.getMessage());
            throw new RuntimeException("Error al iniciar suscripción", e);
        }
    }

    // =================================================================
    // 🛠️ 3. GESTIÓN DE SUSCRIPCIONES Y PORTAL
    // =================================================================

    /**
     * Portal de Facturación: Donde el usuario ve sus facturas y cambia de tarjeta.
     */
    public String createCustomerPortalSession(Long userId, String returnUrl) {
        // Obtenemos el Customer ID directamente de la BD local
        // Nota: Asumimos que ya existe porque para entrar al portal debió haber comprado algo antes.
        String customerId = identityService.getOrCreateCustomer(userId, "unknown@user.com", "User"); 
        
        try {
            com.stripe.param.billingportal.SessionCreateParams params = 
                com.stripe.param.billingportal.SessionCreateParams.builder()
                    .setCustomer(customerId)
                    .setReturnUrl(returnUrl)
                    .build();
            return com.stripe.model.billingportal.Session.create(params).getUrl();
        } catch (StripeException e) {
            throw new RuntimeException("Error creando portal de facturación", e);
        }
    }

    /**
     * Cancelar suscripción al final del periodo (Soft Cancel).
     */
    public void cancelSubscriptionAtPeriodEnd(String subscriptionId) {
        try {
            com.stripe.model.Subscription.retrieve(subscriptionId)
                .update(SubscriptionUpdateParams.builder().setCancelAtPeriodEnd(true).build());
            log.info("⏳ Suscripción {} marcada para cancelar a fin de mes.", subscriptionId);
        } catch (StripeException e) {
            throw new RuntimeException("Error cancelando suscripción", e);
        }
    }

    /**
     * Reactivar suscripción antes de que expire.
     */
    public void resumeSubscription(String subscriptionId) {
        try {
            com.stripe.model.Subscription subscription = com.stripe.model.Subscription.retrieve(subscriptionId);
            if (Boolean.TRUE.equals(subscription.getCancelAtPeriodEnd())) {
                subscription.resume(SubscriptionResumeParams.builder()
                        .setBillingCycleAnchor(SubscriptionResumeParams.BillingCycleAnchor.UNCHANGED)
                        .build());
                log.info("♻️ Suscripción {} reactivada.", subscriptionId);
            }
        } catch (StripeException e) {
            throw new RuntimeException("Error reactivando suscripción", e);
        }
    }

    /**
     * Obtener URL del PDF de la última factura.
     */
    public String getLatestInvoiceUrl(String subscriptionId) {
        try {
            com.stripe.model.Subscription sub = com.stripe.model.Subscription.retrieve(subscriptionId);
            String invoiceId = sub.getLatestInvoice();
            if (invoiceId == null) return null;
            return Invoice.retrieve(invoiceId).getInvoicePdf();
        } catch (StripeException e) {
            log.error("Error obteniendo factura: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 💸 REEMBOLSAR CITA (Total o Parcial)
     * Se usa cuando se cancela una cita pagada.
     * @param paymentIntentId El ID de la transacción (viene del Webhook o de la BD).
     * @param reverseTransfer Si true, le quitamos el dinero al doctor también. Si false, la plataforma absorbe el costo.
     */
    public void refundPayment(String paymentIntentId, boolean reverseTransfer) {
        try {
            RefundCreateParams.Builder paramsBuilder = RefundCreateParams.builder()
                    .setPaymentIntent(paymentIntentId);

            if (reverseTransfer) {
                // Recuperamos el dinero de la cuenta conectada del doctor
                paramsBuilder.setReverseTransfer(true);
            }

            Refund.create(paramsBuilder.build());
            log.info("💸 Reembolso exitoso para PaymentIntent: {}", paymentIntentId);

        } catch (StripeException e) {
            log.error("❌ Error procesando reembolso: {}", e.getMessage());
            throw new RuntimeException("Error al procesar el reembolso", e);
        }
    }

    
}
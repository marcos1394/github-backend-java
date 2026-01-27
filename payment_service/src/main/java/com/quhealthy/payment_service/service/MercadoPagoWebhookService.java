package com.quhealthy.payment_service.service;

import com.mercadopago.MercadoPagoConfig;
import com.mercadopago.client.preapproval.PreapprovalClient;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.resources.preapproval.Preapproval;
import com.quhealthy.payment_service.model.Subscription;
import com.quhealthy.payment_service.model.enums.PaymentGateway;
import com.quhealthy.payment_service.model.enums.SubscriptionStatus;
import com.quhealthy.payment_service.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MercadoPagoWebhookService {

    private final SubscriptionRepository subscriptionRepository;

    @Value("${application.mercadopago.access-token}")
    private String accessToken;

    @Transactional
    public void processNotification(Map<String, Object> payload) {
        String type = (String) payload.get("type");
        String action = (String) payload.get("action");
        
        String entityId = null;
        if (payload.containsKey("data") && payload.get("data") instanceof Map) {
            Map<String, Object> data = (Map<String, Object>) payload.get("data");
            entityId = (String) data.get("id");
        } else if (payload.containsKey("id")) {
            entityId = (String) payload.get("id");
        }

        if (entityId == null) {
            log.warn("⚠️ Webhook MP recibido sin ID. Ignorando.");
            return;
        }

        log.info("📩 Webhook MP: [Type: {}] [ID: {}]", type, entityId);

        if ("subscription_preapproval".equals(type) || "preapproval".equals(type)) {
            handlePreapprovalUpdate(entityId);
        }
    }

    private void handlePreapprovalUpdate(String preapprovalId) {
        try {
            MercadoPagoConfig.setAccessToken(accessToken);
            PreapprovalClient client = new PreapprovalClient();
            Preapproval preapproval = client.get(preapprovalId);

            String externalRef = preapproval.getExternalReference();
            if (externalRef == null || !externalRef.contains("###")) {
                log.warn("⚠️ Suscripción MP {} con referencia inválida: {}", preapprovalId, externalRef);
                // Si no tiene el formato correcto, intentamos leerlo como ID simple por si acaso (compatibilidad)
                return;
            }

            // 👇 RECUPERAMOS LOS DATOS OCULTOS
            String[] parts = externalRef.split("###");
            Long providerId = Long.parseLong(parts[0]);
            String originalPlanId = parts.length > 1 ? parts[1] : null;

            String statusMP = preapproval.getStatus();
            log.info("🔄 Sync MP: {} -> Provider: {} | Status: {}", preapprovalId, providerId, statusMP);

            Optional<Subscription> subOpt = subscriptionRepository.findByExternalSubscriptionId(preapprovalId);
            Subscription subscription;

            if (subOpt.isPresent()) {
                subscription = subOpt.get();
            } else {
                log.info("✨ Nueva suscripción MP creada en BD.");
                subscription = new Subscription();
                subscription.setProviderId(providerId);
                subscription.setExternalSubscriptionId(preapprovalId);
                subscription.setGateway(PaymentGateway.MERCADOPAGO);
                subscription.setCreatedAt(LocalDateTime.now());
                
                // Asignamos el Plan ID que recuperamos del 'tunnel'
                if (originalPlanId != null) {
                    subscription.setPlanId(originalPlanId);
                }
            }

            SubscriptionStatus localStatus = mapStatus(statusMP);
            subscription.setStatus(localStatus);

            if (preapproval.getNextPaymentDate() != null) {
                OffsetDateTime nextPayment = preapproval.getNextPaymentDate();
                subscription.setCurrentPeriodEnd(nextPayment.toLocalDateTime());
                subscription.setCurrentPeriodStart(nextPayment.minusMonths(1).toLocalDateTime());
            }

            subscription.setUpdatedAt(LocalDateTime.now());
            subscriptionRepository.save(subscription);
            
            log.info("✅ BD Actualizada MP: Sub {} -> Estado: {}", preapprovalId, localStatus);

        } catch (Exception e) {
            log.error("❌ Error procesando webhook MP {}: {}", preapprovalId, e.getMessage());
        }
    }

    private SubscriptionStatus mapStatus(String mpStatus) {
        if (mpStatus == null) return SubscriptionStatus.PAST_DUE;
        switch (mpStatus) {
            case "authorized": return SubscriptionStatus.ACTIVE;
            case "paused": return SubscriptionStatus.PAST_DUE;
            case "cancelled": return SubscriptionStatus.CANCELED;
            case "expired": return SubscriptionStatus.CANCELED;
            case "pending": return SubscriptionStatus.PENDING;
            default: return SubscriptionStatus.PAST_DUE;
        }
    }
}
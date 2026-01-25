package com.quhealthy.payment_service.service;

import com.mercadopago.client.preapproval.PreapprovalClient;
import com.mercadopago.client.preapproval.PreapprovalCreateRequest;
// 👇 CORRECCIÓN: Usamos el nombre exacto que aparece en tu lista de archivos
import com.mercadopago.client.preapproval.PreApprovalAutoRecurringCreateRequest;
import com.mercadopago.core.MPRequestOptions;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.resources.preapproval.Preapproval;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Service
public class MercadoPagoService {

    /**
     * Crea una suscripción (Preapproval) usando los nombres de clase exactos del SDK.
     */
    public Preapproval createSubscription(Long providerId, String userEmail, String backUrl, String planId, BigDecimal price) {
        try {
            log.info("🔵 Creando suscripción MercadoPago para Provider: {}", providerId);

            PreapprovalClient client = new PreapprovalClient();

            // Referencia externa única
            String externalRef = "PROV_" + providerId + "_PLAN_" + planId + "_" + UUID.randomUUID().toString().substring(0, 8);

            // Construcción del Request
            PreapprovalCreateRequest request = PreapprovalCreateRequest.builder()
                    .reason("Suscripción QuHealthy - " + planId)
                    .externalReference(externalRef)
                    .payerEmail(userEmail)
                    .backUrl(backUrl)
                    .autoRecurring(
                            // 👇 CORRECCIÓN: Usamos la clase PreApprovalAutoRecurringCreateRequest
                            PreApprovalAutoRecurringCreateRequest.builder()
                                    .frequency(1)
                                    .frequencyType("months")
                                    .transactionAmount(price)
                                    .currencyId("MXN") // Ajusta a tu moneda local
                                    .build()
                    )
                    .status("pending")
                    .build();
            
            // Gestión del Token (Opcional si ya está en config global)
            String token = System.getenv("MP_ACCESS_TOKEN");
            MPRequestOptions options = null;
            
            if(token != null && !token.isBlank()) {
                 options = MPRequestOptions.builder()
                    .accessToken(token)
                    .build();
            }

            // Ejecución
            if (options != null) {
                return client.create(request, options);
            } else {
                return client.create(request);
            }

        } catch (MPException | MPApiException e) {
            log.error("❌ Error creando suscripción MercadoPago: {}", e.getMessage());
            if (e instanceof MPApiException) {
                MPApiException apiEx = (MPApiException) e;
                if (apiEx.getApiResponse() != null) {
                    log.error("MP API Response: {}", apiEx.getApiResponse().getContent());
                }
            }
            throw new RuntimeException("Error al conectar con la pasarela de pagos (MercadoPago)", e);
        }
    }
}
package com.quhealthy.payment_service.service;

import com.mercadopago.MercadoPagoConfig;
import com.mercadopago.client.preapproval.PreApprovalAutoRecurringCreateRequest;
import com.mercadopago.client.preapproval.PreapprovalClient;
import com.mercadopago.client.preapproval.PreapprovalCreateRequest;
import com.mercadopago.client.preapproval.PreapprovalUpdateRequest;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.resources.preapproval.Preapproval;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Slf4j
@Service
public class MercadoPagoService {

    @Value("${application.mp.access-token}")
    private String accessToken;

    @PostConstruct
    public void init() {
        if (accessToken == null || accessToken.isBlank()) {
            log.error("❌ NO se encontró el Access Token de MercadoPago.");
        } else {
            MercadoPagoConfig.setAccessToken(accessToken);
            log.info("🟢 MercadoPago SDK inicializado correctamente.");
        }
    }

    /**
     * Crea una suscripción dinámica basada en datos reales de la BD.
     * @param providerId ID del doctor.
     * @param userEmail Email del doctor.
     * @param backUrl URL de retorno.
     * @param mpPlanId ID de MercadoPago (para el túnel de datos).
     * @param price Precio REAL obtenido de la BD.
     * @param planName Nombre REAL obtenido de la BD (para UX en el checkout).
     */
    public Preapproval createSubscription(Long providerId, String userEmail, String backUrl, String mpPlanId, BigDecimal price, String planName) {
        try {
            log.info("🔵 Iniciando Suscripción MP [Provider: {}] [Plan: {}] [Precio: {}]", providerId, planName, price);

            PreapprovalClient client = new PreapprovalClient();

            // TRUCO ENTERPRISE (Túnel de Datos):
            // Como el SDK no nos deja pasar el Plan ID directo, lo metemos en la referencia externa
            // para poder recuperarlo cuando llegue el Webhook y saber qué plan asignar en BD local.
            // Formato: "ID_USUARIO###ID_PLAN"
            String compositeRef = providerId + "###" + mpPlanId;

            // Construimos la recurrencia manualmente con los datos reales de la BD
            PreApprovalAutoRecurringCreateRequest autoRecurring = PreApprovalAutoRecurringCreateRequest.builder()
                    .frequency(1)
                    .frequencyType("months")
                    .transactionAmount(price) // 👈 Precio real de la BD ($450, $900, etc.)
                    .currencyId("MXN")
                    .build();

            PreapprovalCreateRequest request = PreapprovalCreateRequest.builder()
                    .payerEmail(userEmail)
                    .backUrl(backUrl)
                    // 👇 UX MEJORADA: Mostramos el nombre real del plan en el título
                    .reason("Suscripción QuHealthy: " + planName) 
                    .externalReference(compositeRef) // 👈 Aquí viaja el dato oculto
                    .autoRecurring(autoRecurring)    // 👈 Configuración manual
                    .status("pending")
                    .build();

            Preapproval preapproval = client.create(request);
            
            log.info("✅ Link de Suscripción MP creado: {}", preapproval.getInitPoint());
            return preapproval;

        } catch (MPApiException e) {
            log.error("❌ Error API MercadoPago: {} - {}", e.getStatusCode(), e.getApiResponse().getContent());
            throw new RuntimeException("Error de configuración en MercadoPago: " + e.getMessage());
        } catch (MPException e) {
            log.error("❌ Error de Conexión MercadoPago: {}", e.getMessage());
            throw new RuntimeException("Error de comunicación con la pasarela de pagos.");
        }
    }

    public Preapproval getSubscription(String preapprovalId) {
        try {
            PreapprovalClient client = new PreapprovalClient();
            return client.get(preapprovalId);
        } catch (MPException | MPApiException e) {
            log.error("❌ Error recuperando suscripción MP {}: {}", preapprovalId, e.getMessage());
            throw new RuntimeException("No se pudo sincronizar con MercadoPago.");
        }
    }

    public void cancelSubscription(String preapprovalId) {
        try {
            PreapprovalClient client = new PreapprovalClient();
            PreapprovalUpdateRequest request = PreapprovalUpdateRequest.builder()
                    .status("cancelled")
                    .build();
            client.update(preapprovalId, request);
            log.info("🛑 Suscripción MP {} cancelada.", preapprovalId);
        } catch (MPException | MPApiException e) {
            log.error("❌ Error cancelando suscripción MP: {}", e.getMessage());
            // No lanzamos excepción crítica para no romper flujos masivos, solo logueamos.
        }
    }
    
    public void pauseSubscription(String preapprovalId) {
        try {
            PreapprovalClient client = new PreapprovalClient();
            PreapprovalUpdateRequest request = PreapprovalUpdateRequest.builder()
                    .status("paused")
                    .build();
            client.update(preapprovalId, request);
            log.info("⏸️ Suscripción MP {} pausada.", preapprovalId);
        } catch (MPException | MPApiException e) {
            log.error("❌ Error pausando suscripción MP: {}", e.getMessage());
        }
    }
}
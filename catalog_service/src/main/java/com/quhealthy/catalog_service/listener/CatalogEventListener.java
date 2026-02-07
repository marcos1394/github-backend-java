package com.quhealthy.catalog_service.listener;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.spring.pubsub.support.BasicAcknowledgeablePubsubMessage;
import com.google.cloud.spring.pubsub.support.GcpPubSubHeaders;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.MessageHandler;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class CatalogEventListener {

    private final ObjectMapper objectMapper;

    // Inyectaríamos servicios aquí si necesitamos reaccionar
    // private final CatalogService catalogService;

    /**
     * Escucha eventos globales del sistema (Auth, Payments).
     * Subscription: catalog-service-sub
     */
    @Bean
    @ServiceActivator(inputChannel = "systemInputChannel")
    public MessageHandler messageReceiver() {
        return message -> {
            BasicAcknowledgeablePubsubMessage originalMessage =
                    message.getHeaders().get(GcpPubSubHeaders.ORIGINAL_MESSAGE, BasicAcknowledgeablePubsubMessage.class);

            if (originalMessage != null) {
                try {
                    String payloadStr = new String(originalMessage.getPubsubMessage().getData().toByteArray(), StandardCharsets.UTF_8);
                    log.info("📩 Evento recibido en Catalog: {}", payloadStr);

                    Map<String, Object> eventData = objectMapper.readValue(payloadStr, new TypeReference<>() {});
                    String eventType = (String) eventData.get("eventType");

                    // 🚦 LÓGICA DE REACCIÓN
                    switch (eventType) {
                        case "PLAN_DOWNGRADED":
                            log.info("⚠️ Detectado cambio de plan. Iniciando limpieza de catálogo...");
                            // catalogService.enforcePlanLimits((Long) eventData.get("userId"));
                            break;

                        case "USER_DELETED":
                            log.warn("🗑️ Usuario eliminado. Borrando catálogo...");
                            // catalogService.deleteAllByProvider((Long) eventData.get("userId"));
                            break;

                        default:
                            log.debug("Evento ignorado: {}", eventType);
                    }

                    originalMessage.ack(); // Confirmar recepción
                } catch (Exception e) {
                    log.error("❌ Error procesando mensaje en Catalog: {}", e.getMessage());
                    originalMessage.nack(); // Reintentar
                }
            }
        };
    }
}
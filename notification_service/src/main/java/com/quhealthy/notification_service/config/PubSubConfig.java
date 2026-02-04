package com.quhealthy.notification_service.config;

import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import com.google.cloud.spring.pubsub.integration.AckMode;
import com.google.cloud.spring.pubsub.integration.inbound.PubSubInboundChannelAdapter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty; // ✅ Importante
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.messaging.MessageChannel;

@Configuration
// ✅ CONDICIONAL: Solo carga si Pub/Sub está habilitado.
// matchIfMissing = true significa que en Producción (donde no solemos poner esta propiedad explícitamente) asumirá que es TRUE.
@ConditionalOnProperty(name = "spring.cloud.gcp.pubsub.enabled", matchIfMissing = true)
public class PubSubConfig {

    @Value("${gcp.pubsub.subscription.account}")
    private String accountSubscription;

    @Value("${gcp.pubsub.subscription.appointment}")
    private String appointmentSubscription;

    // ========================================================================
    // 👤 CANAL DE CUENTAS
    // ========================================================================
    @Bean("accountEventInputChannel") // Nombre explícito del Bean
    public MessageChannel accountEventInputChannel() {
        return new DirectChannel();
    }

    @Bean
    public PubSubInboundChannelAdapter accountChannelAdapter(
            @Qualifier("accountEventInputChannel") MessageChannel inputChannel,
            PubSubTemplate pubSubTemplate) {

        PubSubInboundChannelAdapter adapter =
                new PubSubInboundChannelAdapter(pubSubTemplate, accountSubscription);

        adapter.setOutputChannel(inputChannel);
        adapter.setAckMode(AckMode.MANUAL);
        adapter.setPayloadType(String.class);
        return adapter;
    }

    // ========================================================================
    // 📅 CANAL DE CITAS
    // ========================================================================
    @Bean("appointmentEventInputChannel") // Nombre explícito del Bean
    public MessageChannel appointmentEventInputChannel() {
        return new DirectChannel();
    }

    @Bean
    public PubSubInboundChannelAdapter appointmentChannelAdapter(
            @Qualifier("appointmentEventInputChannel") MessageChannel inputChannel,
            PubSubTemplate pubSubTemplate) {

        PubSubInboundChannelAdapter adapter =
                new PubSubInboundChannelAdapter(pubSubTemplate, appointmentSubscription);

        adapter.setOutputChannel(inputChannel);
        adapter.setAckMode(AckMode.MANUAL);
        adapter.setPayloadType(String.class);
        return adapter;
    }
}
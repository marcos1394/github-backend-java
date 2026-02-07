package com.quhealthy.catalog_service.config;

import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import com.google.cloud.spring.pubsub.integration.AckMode;
import com.google.cloud.spring.pubsub.integration.inbound.PubSubInboundChannelAdapter;
import com.google.cloud.spring.pubsub.support.BasicAcknowledgeablePubsubMessage;
import com.google.cloud.spring.pubsub.support.GcpPubSubHeaders;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;

@Configuration
public class PubSubConfig {

    // Nombre de la suscripción en GCP (debe existir previamente o crearse con Terraform)
    // Ej: projects/tu-proyecto/subscriptions/catalog-service-sub
    @Value("${gcp.pubsub.subscription.system:catalog-service-sub}")
    private String systemSubscriptionName;

    /**
     * Canal de entrada (Input Channel).
     * Es como una tubería interna de Java por donde pasarán los mensajes.
     */
    @Bean
    public MessageChannel systemInputChannel() {
        return new DirectChannel();
    }

    /**
     * El Adaptador (Inbound Adapter).
     * Es el "puente" que conecta Google Cloud Pub/Sub con nuestro Canal de Java.
     * Escucha la suscripción y empuja los mensajes al canal.
     */
    @Bean
    public PubSubInboundChannelAdapter messageChannelAdapter(
            @Qualifier("systemInputChannel") MessageChannel inputChannel,
            PubSubTemplate pubSubTemplate
    ) {
        PubSubInboundChannelAdapter adapter =
                new PubSubInboundChannelAdapter(pubSubTemplate, systemSubscriptionName);

        adapter.setOutputChannel(inputChannel);

        // MANUAL: Nosotros decidimos cuándo confirmar (ack) o rechazar (nack) el mensaje.
        // Es más seguro que AUTO para evitar perder mensajes si el sistema falla.
        adapter.setAckMode(AckMode.MANUAL);

        return adapter;
    }
}
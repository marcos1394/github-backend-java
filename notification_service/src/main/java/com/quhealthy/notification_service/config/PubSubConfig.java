package com.quhealthy.notification_service.config;

import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import com.google.cloud.spring.pubsub.integration.AckMode;
import com.google.cloud.spring.pubsub.integration.inbound.PubSubInboundChannelAdapter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.channel.DirectChannel; // 👈 ESTO ANTES FALLABA
import org.springframework.messaging.MessageChannel; // 👈 ESTO ANTES FALLABA

@Configuration
public class PubSubConfig {

    @Bean
    public MessageChannel accountEventInputChannel() {
        return new DirectChannel();
    }

    @Bean
    public PubSubInboundChannelAdapter accountChannelAdapter(
            @Qualifier("accountEventInputChannel") MessageChannel inputChannel,
            PubSubTemplate pubSubTemplate) {
        
        PubSubInboundChannelAdapter adapter =
                new PubSubInboundChannelAdapter(pubSubTemplate, "notification-account-sub");
        
        adapter.setOutputChannel(inputChannel);
        adapter.setAckMode(AckMode.MANUAL);
        adapter.setPayloadType(String.class);
        return adapter;
    }

    @Bean
    public MessageChannel appointmentEventInputChannel() {
        return new DirectChannel();
    }

    @Bean
    public PubSubInboundChannelAdapter appointmentChannelAdapter(
            @Qualifier("appointmentEventInputChannel") MessageChannel inputChannel,
            PubSubTemplate pubSubTemplate) {
        
        PubSubInboundChannelAdapter adapter =
                new PubSubInboundChannelAdapter(pubSubTemplate, "notification-appointment-sub");
        
        adapter.setOutputChannel(inputChannel);
        adapter.setAckMode(AckMode.MANUAL);
        adapter.setPayloadType(String.class);
        return adapter;
    }
}
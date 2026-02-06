package com.quhealthy.payment_service.listener;

import com.quhealthy.payment_service.dto.UserEvent;
import com.quhealthy.payment_service.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.function.Consumer;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class RegistrationEventListener {

    private final SubscriptionService subscriptionService;

    @Bean
    public Consumer<UserEvent> userRegisteredConsumer() {
        return event -> {
            log.info("📩 Evento recibido: USER_REGISTERED para {}", event.getEmail());

            if ("PROVIDER".equals(event.getRole())) {
                Map<String, Object> payload = event.getPayload();

                // Verificamos si es un registro nuevo que requiere Trial/Plan Gratuito
                if (payload != null && Boolean.TRUE.equals(payload.get("isTrial"))) {

                    String name = (String) payload.getOrDefault("name", "Usuario QuHealthy");

                    subscriptionService.createInitialFreeSubscription(
                            event.getUserId(),
                            event.getEmail(),
                            name
                    );
                }
            }
        };
    }
}
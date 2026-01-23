package com.quhealthy.social_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    /**
     * Crea un Bean de WebClient configurado globalmente.
     * Se usará para comunicarse con las APIs de Graph (Meta), TikTok y LinkedIn.
     */
    @Bean
    public WebClient webClient() {
        return WebClient.builder()
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                // Aquí podrías agregar timeouts globales o logs si quisieras
                .build();
    }
}
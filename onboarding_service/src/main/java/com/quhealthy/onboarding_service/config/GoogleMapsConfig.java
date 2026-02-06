package com.quhealthy.onboarding_service.config;

import com.google.maps.GeoApiContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Slf4j
@Configuration
public class GoogleMapsConfig {

    @Value("${google.maps.api-key}")
    private String apiKey;

    /**
     * Singleton del contexto de Google Maps.
     * Maneja autenticación, reintentos (backoff) y rate limiting automáticamente.
     */
    @Bean
    public GeoApiContext geoApiContext() {
        log.info("🗺️ Inicializando Google Maps Context...");
        return new GeoApiContext.Builder()
                .apiKey(apiKey)
                // Configuración Enterprise para resiliencia:
                .retryTimeout(5, TimeUnit.SECONDS) // Reintentar hasta por 5 segundos si falla la red
                .maxRetries(3) // Máximo 3 intentos en caso de error 5xx
                .queryRateLimit(50) // Límite de seguridad: 50 consultas/segundo (ajustable)
                .build();
    }
}
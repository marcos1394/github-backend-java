package com.quhealthy.notification_service;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.TimeZone;

@SpringBootApplication
@EnableAsync // Habilita procesamiento asíncrono si decidimos usar @Async en el futuro
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }

    /**
     * 🌍 Configuración Global de Zona Horaria (UTC).
     * Esto asegura que los timestamps de las notificaciones sean consistentes
     * sin importar en qué servidor del mundo se ejecute el contenedor.
     */
    @PostConstruct
    public void init() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }
}
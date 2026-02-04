package com.quhealthy.notification_service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@Slf4j
@SpringBootApplication
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
        log.info("🕒 TimeZone configurada a UTC para consistencia global.");
    }
}
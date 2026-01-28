package com.quhealthy.appointment_service;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

import java.util.TimeZone;

@SpringBootApplication
@EnableFeignClients // 👈 VITAL: Sin esto, no funciona la conexión con Catalog Service
public class AppointmentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AppointmentServiceApplication.class, args);
    }

    /**
     * 🌍 Configuración Global de Zona Horaria (UTC).
     * Para sistemas de agenda médica, JAMÁS uses la hora del sistema operativo.
     * Siempre UTC en backend, y el Frontend convierte a la hora local del usuario.
     */
    @PostConstruct
    public void init() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }
}
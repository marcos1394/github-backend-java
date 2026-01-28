package com.quhealthy.review_service;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class ReviewServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReviewServiceApplication.class, args);
    }

    /**
     * Configuración Global de Zona Horaria.
     * Forzamos a que toda la aplicación corra en UTC para evitar desfases
     * de horas entre la base de datos y el servidor de aplicaciones.
     */
    @PostConstruct
    public void init() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }
}
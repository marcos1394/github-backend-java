package com.quhealthy.catalog_service;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class CatalogServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CatalogServiceApplication.class, args);
    }

    /**
     * 🌍 Configuración Global de Zona Horaria (UTC).
     * Esto asegura que las fechas de creación/modificación en la BD
     * sean consistentes sin importar si el servidor corre en AWS, GCP o local.
     */
    @PostConstruct
    public void init() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }
}
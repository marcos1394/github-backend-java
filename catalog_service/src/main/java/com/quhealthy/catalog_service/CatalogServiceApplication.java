package com.quhealthy.catalog_service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@Slf4j
@SpringBootApplication
public class CatalogServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CatalogServiceApplication.class, args);
    }

    /**
     * 🌍 Configuración Global de Zona Horaria (UTC).
     * Vital para que las fechas de creación/actualización de productos
     * sean consistentes sin importar dónde esté el servidor.
     */
    @PostConstruct
    public void init() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        log.info("🕒 Catalog Service iniciado. TimeZone configurada a UTC.");
    }
}
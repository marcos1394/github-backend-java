package com.quhealthy.catalog_service.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestTemplate;

@Configuration
public class AppConfig {

    /**
     * Configuración JSON Robustecida.
     * Vital para el Catalog Service ya que manejaremos columnas JSONB en PostgreSQL.
     * Esta configuración asegura que la metadata flexible de los productos se serialice correctamente.
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // Manejo correcto de fechas (Java 8 LocalDateTime)
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // ✅ CRÍTICO: Ignora campos desconocidos.
        // Esto permite que si en el futuro agregamos atributos nuevos a un Producto en la BD (JSONB),
        // el código antiguo no rompa al intentar leerlo.
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        return mapper;
    }

    /**
     * Cliente HTTP Estándar.
     * Aunque el catálogo es principalmente CRUD, dejamos listo el cliente para:
     * 1. Futura comunicación síncrona con otros microservicios (ej: Order Service validando stock).
     * 2. Integraciones futuras (ej: Sincronizar catálogo con proveedores externos).
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
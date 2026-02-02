package com.quhealthy.auth_service.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Configuración dedicada para JPA.
 *
 * RESPONSABILIDAD:
 * Activar la auditoría automática (llenado de createdAt, updatedAt).
 *
 * ¿POR QUÉ ESTÁ AQUÍ?
 * Al separarlo de la clase Main, permitimos que los tests de tipo @WebMvcTest
 * (que no cargan base de datos) puedan arrancar sin fallar buscando entidades JPA.
 */
@Configuration
@EnableJpaAuditing // ⚠️ CRÍTICO: Aquí es donde debe vivir ahora.
public class JpaConfig {
}
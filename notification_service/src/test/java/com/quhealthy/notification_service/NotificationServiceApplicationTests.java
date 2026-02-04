package com.quhealthy.notification_service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest // Carga el contexto completo de Spring
class NotificationServiceApplicationTests extends AbstractIntegrationTest { // ✅ CRÍTICO: Heredar Docker

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    @DisplayName("Smoke Test: El contexto carga y Docker está vivo")
    void contextLoads() {
        // ✅ Prueba de Humo (Smoke Test)
        // Verifica que:
        // 1. El contenedor de Docker Singleton está vivo y accesible.
        // 2. Spring pudo conectarse a la BD PostgreSQL.
        // 3. Todos los Beans principales se inicializaron.

        assertThat(applicationContext).isNotNull();

        // Verificación extra opcional: comprobar que hay beans clave
        assertThat(applicationContext.containsBean("notificationService")).isTrue();

        System.out.println("✅ [TEST] El contexto de Spring cargó exitosamente con Testcontainers.");
    }
}
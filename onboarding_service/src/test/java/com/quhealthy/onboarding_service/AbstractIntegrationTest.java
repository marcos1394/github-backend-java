package com.quhealthy.onboarding_service;

import com.quhealthy.onboarding_service.config.TestConfig;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 🐳 CLASE BASE PARA TESTS DE INTEGRACIÓN
 * Levanta un contenedor de PostgreSQL real antes de ejecutar los tests.
 * Todos los tests que hereden de aquí usarán esta misma BD limpia.
 */
@SpringBootTest(classes = OnboardingServiceApplication.class) // 👈 A veces ayuda ser explícito con la clase Main
@ActiveProfiles("test")
@Import(TestConfig.class)
@Testcontainers
public abstract class AbstractIntegrationTest {

    // Definimos el contenedor de PostgreSQL (versión debe coincidir con Producción)
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("quhealthy_onboarding_test")
            .withUsername("test_user")
            .withPassword("test_pass");

    static {
        // Arrancamos el contenedor manualmente para asegurar patrón Singleton
        postgres.start();
    }

    /**
     * Sobrescribe las propiedades de conexión de Spring Datasource
     * con las credenciales dinámicas que generó Testcontainers.
     */
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        // Opcional: Aseguramos que Hibernate cree las tablas desde cero
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }
}
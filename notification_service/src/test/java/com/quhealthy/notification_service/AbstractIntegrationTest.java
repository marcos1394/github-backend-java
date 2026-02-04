package com.quhealthy.notification_service;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

/**
 * ============================================================================
 * CLASE BASE (SINGLETON CONTAINER PATTERN)
 * ============================================================================
 * Soluciona el error "Connection Refused" iniciando la BD una sola vez
 * y compartiéndola entre todos los tests.
 */
// ❌ QUITAMOS @SpringBootTest de aquí para permitir que los hijos decidan
// si quieren ser @DataJpaTest (ligero) o @SpringBootTest (pesado).
// ❌ QUITAMOS @Testcontainers para tomar control manual.
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    // Definimos la imagen. Usamos 'postgres:15-alpine' como pediste.
    // (Nota: Si usas GEO/Mapas en el futuro, cambia a 'postgis/postgis:15-3.3')
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:15-alpine")
                    .asCompatibleSubstituteFor("postgres")
    )
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test")
            .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(60)));

    // 🔥 INICIO MANUAL BLOQUEANTE (El secreto del éxito)
    // Esto arranca Docker ANTES de que Spring cargue nada.
    static {
        postgres.start();
        // Opcional: System.setProperty para depuración si falla
        System.setProperty("DB_URL", postgres.getJdbcUrl());
        System.setProperty("DB_USER", postgres.getUsername());
        System.setProperty("DB_PASSWORD", postgres.getPassword());
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }
}
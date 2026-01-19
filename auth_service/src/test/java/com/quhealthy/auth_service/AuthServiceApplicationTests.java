package com.quhealthy.auth_service; // (Asegúrate que coincida con tu paquete)

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName; // <--- Importante

@SpringBootTest
@Testcontainers // 1. Habilita el soporte para contenedores
@ActiveProfiles("test")
class AuthServiceApplicationTests {

    // DEFINICIÓN DEL CONTENEDOR CORREGIDA 🛠️
    // 1. Usamos DockerImageName.parse(...) para definir la imagen.
    // 2. Agregamos .asCompatibleSubstituteFor("postgres") para decirle a Testcontainers
    //    que PostGIS es un Postgres válido y quitar el error.
    @SuppressWarnings("resource")
    @Container
    static PostgreSQLContainer<?> postgis = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:15-3.3")
                .asCompatibleSubstituteFor("postgres")
    )
    .withDatabaseName("testdb")
    .withUsername("test")
    .withPassword("test");

    // 3. Inyectamos la URL dinámica del contenedor a Spring Boot
    // (Sobrescribe lo que tengas en application.properties)
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgis::getJdbcUrl);
        registry.add("spring.datasource.password", postgis::getPassword);
        registry.add("spring.datasource.username", postgis::getUsername);
        // Aseguramos que Hibernate use el dialecto correcto
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @Test
    void contextLoads() {
        // Ahora sí, este test levantará un Postgres real con soporte Geográfico.
        // Si la app arranca aquí, arranca en producción seguro.
    }
}
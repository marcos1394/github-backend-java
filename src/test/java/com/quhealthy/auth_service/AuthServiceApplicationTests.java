package com.quhealthy.auth_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test") // <--- ESTA LÍNEA ES LA SOLUCIÓN. Obliga a usar application-test.properties
class AuthServiceApplicationTests {

    @Test
    void contextLoads() {
        // Este test verifica que Spring Boot pueda levantar (inyección de dependencias, 
        // conexión a BD en memoria, lectura de configs) sin explotar.
        // Si pasa esto, el Deploy es seguro.
    }

}
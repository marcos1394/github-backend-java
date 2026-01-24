package com.quhealthy.social_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// ✅ SOLUCIÓN: Usamos 'excludeName' con la ruta completa en texto.
// Esto evita el error de "package does not exist" al compilar.
@SpringBootApplication(excludeName = { 
    "org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration" 
})
@EnableScheduling
public class SocialServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SocialServiceApplication.class, args);
    }

}
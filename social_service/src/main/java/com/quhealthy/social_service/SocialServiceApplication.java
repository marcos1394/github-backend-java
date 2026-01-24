package com.quhealthy.social_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

// 1. exclude: Evita que Spring cree un usuario/password por defecto en consola.
// 2. EnableScheduling: Activa el Cron Job para publicar posts automáticamente.
@SpringBootApplication(exclude = { UserDetailsServiceAutoConfiguration.class })
@EnableScheduling 
public class SocialServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SocialServiceApplication.class, args);
    }

}
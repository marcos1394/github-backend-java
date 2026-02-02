package com.quhealthy.auth_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// ✅ CORRECCIÓN: Quitamos @EnableJpaAuditing de aquí.
// Ahora la aplicación principal es "neutra" y no fuerza la carga de JPA
// cuando corremos tests ligeros de Controladores.
@SpringBootApplication
public class AuthServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(AuthServiceApplication.class, args);
	}

}
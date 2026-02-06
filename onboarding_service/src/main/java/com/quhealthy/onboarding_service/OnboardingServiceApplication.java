package com.quhealthy.onboarding_service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
// import org.springframework.data.jpa.repository.config.EnableJpaAuditing; // 🗑️ BORRAR ESTA LÍNEA

import java.util.TimeZone;

@Slf4j
@SpringBootApplication
// @EnableJpaAuditing  <--- 🗑️ BORRAR ESTA LÍNEA TAMBIÉN
public class OnboardingServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(OnboardingServiceApplication.class, args);
	}

	/**
	 * 🌍 Configuración Global de Zona Horaria (UTC).
	 */
	@PostConstruct
	public void init() {
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
		log.info("🕒 Onboarding Service iniciado. TimeZone configurada a UTC.");
	}
}
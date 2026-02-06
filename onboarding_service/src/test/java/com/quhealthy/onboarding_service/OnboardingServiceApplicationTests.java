package com.quhealthy.onboarding_service;

import com.quhealthy.onboarding_service.controller.OnboardingController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test; // 👈 Asegúrate que sea org.junit.jupiter.api
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

// ✅ DEBE EXTENDER DE AbstractIntegrationTest
class OnboardingServiceApplicationTests extends AbstractIntegrationTest {

	@Autowired
	private ApplicationContext applicationContext;

	@Autowired(required = false) // Ponlo false por si acaso falla la inyección específica
	private OnboardingController onboardingController;

	@Test
	@DisplayName("🔥 Smoke Test: Contexto Spring + Docker PostgreSQL cargan correctamente")
	void contextLoads() {
		// Validación básica
		assertThat(applicationContext).isNotNull();

		// Validación de que el contexto levantó los controllers
		assertThat(onboardingController).isNotNull();

		System.out.println("✅ [TEST] Contexto de Onboarding Service levantado OK.");
	}
}
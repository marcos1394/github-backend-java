package com.quhealthy.onboarding_service.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
@EnableJpaAuditing // ✅ Activa la "auditoría" (fechas automáticas)
public class JpaConfig {
}
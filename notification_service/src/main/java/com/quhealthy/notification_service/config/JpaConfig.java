package com.quhealthy.notification_service.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
@EnableJpaAuditing // ✅ Activa el llenado automático de fechas
public class JpaConfig {
}
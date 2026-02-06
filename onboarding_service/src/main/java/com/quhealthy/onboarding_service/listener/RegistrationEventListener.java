package com.quhealthy.onboarding_service.listener;

import com.quhealthy.onboarding_service.event.UserEvent;
import com.quhealthy.onboarding_service.model.ProviderOnboarding;
import com.quhealthy.onboarding_service.model.enums.OnboardingStatus;
import com.quhealthy.onboarding_service.repository.ProviderOnboardingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.function.Consumer;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class RegistrationEventListener {

    private final ProviderOnboardingRepository onboardingRepository;

    @Bean
    public Consumer<UserEvent> userRegisteredConsumer() {
        return event -> {
            log.info("📩 [Onboarding] Evento recibido: USER_REGISTERED para {}", event.getEmail());

            // Solo nos interesan los proveedores (los pacientes no hacen onboarding profesional)
            if ("PROVIDER".equals(event.getRole())) {
                createOnboardingChecklist(event.getUserId(), event.getPayload());
            }
        };
    }

    private void createOnboardingChecklist(Long userId, Map<String, Object> payload) {
        // 1. Idempotencia: ¿Ya existe el checklist? (Evitar reinicios accidentales)
        if (onboardingRepository.existsById(userId)) {
            log.warn("⚠️ El checklist de onboarding ya existe para el usuario {}.", userId);
            return;
        }

        // 2. Extraer Plan ID (Para saber qué pasos activar en el futuro)
        Long planId = null;
        if (payload != null && payload.containsKey("planId")) {
            Object planIdObj = payload.get("planId");
            // Manejo seguro de tipos (Integer a Long)
            if (planIdObj instanceof Number) {
                planId = ((Number) planIdObj).longValue();
            } else if (planIdObj instanceof String) {
                planId = Long.parseLong((String) planIdObj);
            }
        }

        // 3. Crear la fila inicial (Todo en PENDING por defecto)
        ProviderOnboarding initialStatus = ProviderOnboarding.builder()
                .providerId(userId)
                .selectedPlanId(planId)

                // Inicializamos los estados explícitamente (aunque @Builder.Default ayuda)
                .profileStatus(OnboardingStatus.PENDING)
                .kycStatus(OnboardingStatus.PENDING)
                .licenseStatus(OnboardingStatus.PENDING)
                .fiscalStatus(OnboardingStatus.PENDING)
                .marketplaceStatus(OnboardingStatus.PENDING)

                // Auditoría manual si JPA Auditing no captura el contexto asíncrono
                // (A veces en listeners async, SecurityContext está vacío)
                .build();

        // Si usas BaseEntity con @CreatedDate, asegúrate de que funcione,
        // si no, setea createdAt manualmente aquí.
        initialStatus.setCreatedAt(LocalDateTime.now());

        onboardingRepository.save(initialStatus);
        log.info("✅ Checklist de Onboarding inicializado para usuario {} (Plan ID: {})", userId, planId);
    }
}
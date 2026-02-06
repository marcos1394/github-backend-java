package com.quhealthy.auth_service.service;

import com.quhealthy.auth_service.dto.request.RegisterConsumerRequest;
import com.quhealthy.auth_service.dto.request.RegisterProviderRequest;
import com.quhealthy.auth_service.dto.response.ConsumerRegistrationResponse;
import com.quhealthy.auth_service.dto.response.ProviderRegistrationResponse;
import com.quhealthy.auth_service.event.UserEvent;
import com.quhealthy.auth_service.event.UserEventPublisher;
import com.quhealthy.auth_service.model.Consumer;
import com.quhealthy.auth_service.model.Plan;
import com.quhealthy.auth_service.model.Provider;
import com.quhealthy.auth_service.repository.ConsumerRepository;
import com.quhealthy.auth_service.repository.PlanRepository;
import com.quhealthy.auth_service.repository.ProviderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RegistrationService {

    private final ConsumerRepository consumerRepository;
    private final ProviderRepository providerRepository;
    private final PlanRepository planRepository; // ✅ NECESARIO para buscar el Plan Trial
    private final PasswordEncoder passwordEncoder;
    private final UserEventPublisher eventPublisher;

    // 🎁 CONSTANTES DEL PLAN GRATUITO (TRIAL)
    private static final Long FREE_PLAN_ID = 5L;
    private static final String FREE_PLAN_NAME = "Plan Gratuito";
    private static final int TRIAL_DAYS = 30;

    /**
     * Registra un nuevo Paciente (Consumer).
     */
    @Transactional
    public ConsumerRegistrationResponse registerConsumer(RegisterConsumerRequest request) {
        log.info("Iniciando registro de consumidor: {}", request.getEmail());

        // 1. Validar unicidad del email (Global)
        validateEmailUniqueness(request.getEmail());

        // 2. Generar token de verificación
        String verificationToken = UUID.randomUUID().toString();

        // 3. Crear Entidad
        Consumer consumer = Consumer.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                // Estados Iniciales
                .emailVerificationToken(verificationToken)
                .isEmailVerified(false)
                .isPhoneVerified(false)
                // Legal
                .termsAccepted(request.isTermsAccepted())
                // Auditoría
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // 4. Guardar en BD
        Consumer savedConsumer = consumerRepository.save(consumer);

        // 5. Preparar Datos Extra
        Map<String, Object> extraData = new HashMap<>();
        if (request.getReferralCode() != null) extraData.put("referralCode", request.getReferralCode());
        if (request.getUtmSource() != null) extraData.put("utmSource", request.getUtmSource());
        if (request.getUtmMedium() != null) extraData.put("utmMedium", request.getUtmMedium());

        // 6. Publicar evento
        publishRegistrationEvent(
                savedConsumer.getId(),
                savedConsumer.getEmail(),
                "CONSUMER",
                verificationToken,
                savedConsumer.getFirstName(),
                extraData
        );

        // 7. Retornar respuesta
        return ConsumerRegistrationResponse.builder()
                .id(savedConsumer.getId())
                .email(savedConsumer.getEmail())
                .firstName(savedConsumer.getFirstName())
                .message("Cuenta creada exitosamente. Por favor verifica tu correo.")
                .createdAt(savedConsumer.getCreatedAt())
                .build();
    }

    /**
     * Registra un nuevo Proveedor (Provider).
     * ASIGNA AUTOMÁTICAMENTE EL PLAN GRATUITO DE 30 DÍAS.
     */
    @Transactional
    public ProviderRegistrationResponse registerProvider(RegisterProviderRequest request) {
        log.info("Iniciando registro de proveedor: {}", request.getEmail());

        // 1. Validar unicidad
        validateEmailUniqueness(request.getEmail());

        // 2. 🔍 BUSCAR EL PLAN GRATUITO (CORRECCIÓN CRÍTICA)
        // Buscamos la entidad Plan real para mantener la integridad referencial.
        Plan freePlan = planRepository.findById(FREE_PLAN_ID)
                .orElseThrow(() -> new IllegalStateException("Error crítico: El Plan Gratuito (ID " + FREE_PLAN_ID + ") no está configurado en la base de datos."));

        // 3. Token de verificación
        String verificationToken = UUID.randomUUID().toString();

        // 4. Crear Entidad Provider
        Provider provider = Provider.builder()
                // Identidad Personal
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .phone(request.getPhone())

                // Identidad Negocio
                .businessName(request.getBusinessName())

                // Categorización Inicial (Solo Industria)
                .parentCategoryId(request.getParentCategoryId())
                .category(null)     // Se llenará en el wizard
                .subCategory(null)  // Se llenará en el wizard

                // Estados y Legal
                .emailVerificationToken(verificationToken)
                .isEmailVerified(false)
                .termsAccepted(request.isTermsAccepted())

                // ⚠️ CAMPOS NUEVOS OBLIGATORIOS (Fix del modelo Provider)
                .onboardingComplete(false)
                .onboardingStatus("PROFILE_PENDING") // Estado explícito para el Frontend
                .kycStatus("PENDING")                // KYC explícito

                // 🎁 ASIGNACIÓN DEL PLAN (Fix de la relación ManyToOne)
                .plan(freePlan)      // ✅ Pasamos el objeto, no el ID
                .hasActivePlan(true) // Nace activo

                // Auditoría
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // 5. Guardar
        Provider savedProvider = providerRepository.save(provider);

        // 6. Datos Extra para Eventos (Incluyendo Trial)
        Map<String, Object> extraData = new HashMap<>();
        extraData.put("parentCategoryId", request.getParentCategoryId());

        // --- DATA DEL PLAN (Para Notification & Payment Service) ---
        extraData.put("planId", freePlan.getId());
        extraData.put("planName", freePlan.getName());
        extraData.put("trialStartDate", LocalDateTime.now().toString());
        extraData.put("trialEndDate", LocalDateTime.now().plusDays(TRIAL_DAYS).toString());
        extraData.put("isTrial", true);
        // -----------------------------------------------------------

        if (request.getReferralCode() != null) extraData.put("referralCode", request.getReferralCode());
        if (request.getUtmSource() != null) extraData.put("utmSource", request.getUtmSource());
        if (request.getUtmMedium() != null) extraData.put("utmMedium", request.getUtmMedium());

        // 7. Publicar Evento
        publishRegistrationEvent(
                savedProvider.getId(),
                savedProvider.getEmail(),
                "PROVIDER",
                verificationToken,
                savedProvider.getFirstName(),
                extraData
        );

        // 8. Respuesta
        return ProviderRegistrationResponse.builder()
                .id(savedProvider.getId())
                .email(savedProvider.getEmail())
                .businessName(savedProvider.getBusinessName())
                .firstName(savedProvider.getFirstName())
                .message("Cuenta profesional creada con Plan Gratuito.")
                .createdAt(savedProvider.getCreatedAt())
                .build();
    }

    // ========================================================================
    // 🔒 MÉTODOS PRIVADOS DE APOYO
    // ========================================================================

    private void validateEmailUniqueness(String email) {
        if (consumerRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("El correo electrónico ya está registrado como Paciente.");
        }
        if (providerRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("El correo electrónico ya está registrado como Profesional.");
        }
    }

    private void publishRegistrationEvent(Long userId, String email, String role, String token, String name, Map<String, Object> extraData) {
        Map<String, Object> payload = new HashMap<>();

        // Datos Core para Notificaciones
        payload.put("verificationToken", token);
        payload.put("name", name);

        // Inyectamos los datos extra
        if (extraData != null) {
            payload.putAll(extraData);
        }

        UserEvent event = UserEvent.builder()
                .eventType("USER_REGISTERED")
                .userId(userId)
                .email(email)
                .role(role)
                .payload(payload)
                .timestamp(LocalDateTime.now())
                .build();

        eventPublisher.publish(event);
    }
}
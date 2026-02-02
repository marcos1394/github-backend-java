package com.quhealthy.auth_service.service;

import com.quhealthy.auth_service.dto.request.RegisterConsumerRequest;
import com.quhealthy.auth_service.dto.request.RegisterProviderRequest;
import com.quhealthy.auth_service.dto.response.ConsumerRegistrationResponse;
import com.quhealthy.auth_service.dto.response.ProviderRegistrationResponse;
import com.quhealthy.auth_service.event.UserEvent;
import com.quhealthy.auth_service.event.UserEventPublisher; // ✅ USO DE LA INTERFAZ
import com.quhealthy.auth_service.model.Consumer;
import com.quhealthy.auth_service.model.Provider;
import com.quhealthy.auth_service.repository.ConsumerRepository;
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
    private final PasswordEncoder passwordEncoder;

    // ✅ INYECCIÓN DE DEPENDENCIA DE LA INTERFAZ
    // Spring inyectará PubSubUserEventPublisher (Prod) o NoOpUserEventPublisher (Test)
    private final UserEventPublisher eventPublisher;

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
     */
    @Transactional
    public ProviderRegistrationResponse registerProvider(RegisterProviderRequest request) {
        log.info("Iniciando registro de proveedor: {}", request.getEmail());

        // 1. Validar unicidad
        validateEmailUniqueness(request.getEmail());

        // 2. Token
        String verificationToken = UUID.randomUUID().toString();

        // 3. Crear Entidad Provider
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
                .onboardingComplete(false)
                .termsAccepted(request.isTermsAccepted())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // 4. Guardar
        Provider savedProvider = providerRepository.save(provider);

        // 5. Datos Extra para Eventos
        Map<String, Object> extraData = new HashMap<>();
        extraData.put("parentCategoryId", request.getParentCategoryId());
        if (request.getReferralCode() != null) extraData.put("referralCode", request.getReferralCode());
        if (request.getUtmSource() != null) extraData.put("utmSource", request.getUtmSource());
        if (request.getUtmMedium() != null) extraData.put("utmMedium", request.getUtmMedium());

        // 6. Publicar Evento
        publishRegistrationEvent(
                savedProvider.getId(),
                savedProvider.getEmail(),
                "PROVIDER",
                verificationToken,
                savedProvider.getFirstName(),
                extraData
        );

        // 7. Respuesta
        return ProviderRegistrationResponse.builder()
                .id(savedProvider.getId())
                .email(savedProvider.getEmail())
                .businessName(savedProvider.getBusinessName())
                .firstName(savedProvider.getFirstName())
                .message("Cuenta profesional creada. Verifica tu email para configurar tu perfil.")
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

    /**
     * Construye y publica el evento unificado.
     */
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

        // ✅ LLAMADA A TRAVÉS DE LA INTERFAZ
        eventPublisher.publish(event);
    }
}
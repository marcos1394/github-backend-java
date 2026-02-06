package com.quhealthy.auth_service.service;

import com.quhealthy.auth_service.dto.request.LoginRequest;
import com.quhealthy.auth_service.dto.response.AuthResponse;
import com.quhealthy.auth_service.model.BaseUser;
import com.quhealthy.auth_service.model.Consumer;
import com.quhealthy.auth_service.model.Provider;
import com.quhealthy.auth_service.repository.ConsumerRepository;
import com.quhealthy.auth_service.repository.ProviderRepository;
import com.quhealthy.auth_service.service.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final ConsumerRepository consumerRepository;
    private final ProviderRepository providerRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    /**
     * Proceso de Login Unificado.
     * Busca en ambas tablas (Provider/Consumer), valida password e inyecta
     * la identidad completa (Plan, KYC, Onboarding) en el Token.
     */
    @Transactional(readOnly = true)
    public AuthResponse authenticate(LoginRequest request) {
        log.info("Intento de autenticación para: {}", request.getEmail());

        // 1. Intentar buscar como CONSUMER (Paciente)
        Optional<Consumer> consumerOpt = consumerRepository.findByEmail(request.getEmail());
        if (consumerOpt.isPresent()) {
            return authenticateConsumer(consumerOpt.get(), request.getPassword());
        }

        // 2. Intentar buscar como PROVIDER (Doctor/Especialista)
        Optional<Provider> providerOpt = providerRepository.findByEmail(request.getEmail());
        if (providerOpt.isPresent()) {
            return authenticateProvider(providerOpt.get(), request.getPassword());
        }

        // 3. Si no existe en ninguno
        log.warn("Login fallido: Usuario no encontrado {}", request.getEmail());
        throw new BadCredentialsException("Correo electrónico o contraseña incorrectos");
    }

    // ========================================================================
    // 🔒 LÓGICA ESPECÍFICA CONSUMER
    // ========================================================================

    private AuthResponse authenticateConsumer(Consumer consumer, String rawPassword) {
        validateCredentials(consumer, rawPassword);

        // 1. Preparar Claims (Datos del Pasaporte Digital)
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("id", consumer.getId());
        extraClaims.put("role", "CONSUMER");
        // Los consumers no tienen plan ni KYC complejo por ahora.

        // 2. Generar Token
        String token = jwtService.generateToken(extraClaims, consumer);

        // 3. Construir Status para Frontend
        AuthResponse.AuthStatus status = AuthResponse.AuthStatus.builder()
                .isEmailVerified(consumer.isEmailVerified())
                .isPhoneVerified(consumer.isPhoneVerified())
                .onboardingComplete(true) // Pacientes siempre entran directo
                .hasActivePlan(true)      // Siempre activo
                .build();

        return AuthResponse.builder()
                .token(token)
                .role("CONSUMER")
                .message("Bienvenido, " + consumer.getFirstName())
                .status(status)
                .build();
    }

    // ========================================================================
    // 🔒 LÓGICA ESPECÍFICA PROVIDER (AQUÍ ESTÁ LA MAGIA)
    // ========================================================================

    private AuthResponse authenticateProvider(Provider provider, String rawPassword) {
        validateCredentials(provider, rawPassword);

        // 1. Preparar Claims (Single Source of Truth)
        Map<String, Object> extraClaims = new HashMap<>();

        extraClaims.put("id", provider.getId());
        extraClaims.put("role", "PROVIDER");

        // --- INYECCIÓN DEL PLAN ---
        // Si el provider tiene plan asignado, usamos su ID. Si no (null), forzamos Plan Gratuito (5).
        Long planId = (provider.getPlan() != null) ? provider.getPlan().getId() : 5L;
        extraClaims.put("planId", planId);

        // --- INYECCIÓN DE ESTADOS (Onboarding & KYC) ---
        extraClaims.put("onboardingStatus", provider.getOnboardingStatus());
        extraClaims.put("kycStatus", provider.getKycStatus());

        // 2. Generar Token Supervitaminado 💊
        String token = jwtService.generateToken(extraClaims, provider);

        // 3. Construir Status para Frontend
        AuthResponse.AuthStatus status = AuthResponse.AuthStatus.builder()
                .isEmailVerified(provider.isEmailVerified())
                .isPhoneVerified(provider.isPhoneVerified())
                .onboardingComplete("COMPLETED".equals(provider.getOnboardingStatus()))
                .hasActivePlan(provider.isHasActivePlan())
                .build();

        return AuthResponse.builder()
                .token(token)
                .role("PROVIDER")
                .message("Bienvenido al Dashboard, " + provider.getBusinessName())
                .status(status)
                .build();
    }

    // ========================================================================
    // 🛠️ VALIDACIONES COMUNES
    // ========================================================================

    private void validateCredentials(BaseUser user, String rawPassword) {
        if (!user.isEnabled()) {
            throw new DisabledException("La cuenta está deshabilitada. Contacta soporte.");
        }
        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            log.warn("Login fallido: Password incorrecto para {}", user.getEmail());
            throw new BadCredentialsException("Correo electrónico o contraseña incorrectos");
        }
    }
}
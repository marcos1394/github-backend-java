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

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final ConsumerRepository consumerRepository;
    private final ProviderRepository providerRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService; // Se creará en el siguiente paso

    /**
     * Proceso de Login unificado.
     * 1. Busca usuario en ambas tablas.
     * 2. Valida credenciales.
     * 3. Genera Token.
     * 4. Calcula el estado (AuthStatus) para redirección en Frontend.
     */
    @Transactional(readOnly = true)
    public AuthResponse authenticate(LoginRequest request) {
        log.info("Intento de autenticación para: {}", request.getEmail());

        // 1. Intentar buscar como CONSUMER
        Optional<Consumer> consumerOpt = consumerRepository.findByEmail(request.getEmail());
        if (consumerOpt.isPresent()) {
            return authenticateConsumer(consumerOpt.get(), request.getPassword());
        }

        // 2. Intentar buscar como PROVIDER
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

        String token = jwtService.generateToken(consumer);
        // String refreshToken = jwtService.generateRefreshToken(consumer); // Futuro

        // Construir Status para Consumer
        // Nota: Los consumers suelen tener onboardingComplete=true y hasActivePlan=true por defecto
        AuthResponse.AuthStatus status = AuthResponse.AuthStatus.builder()
                .isEmailVerified(consumer.isEmailVerified())
                .isPhoneVerified(consumer.isPhoneVerified())
                .onboardingComplete(true) // Pacientes entran directo
                .hasActivePlan(true)      // Pacientes no pagan membresía de uso
                .build();

        return AuthResponse.builder()
                .token(token)
                .role("CONSUMER")
                .message("Bienvenido, " + consumer.getFirstName())
                .status(status)
                .build();
    }

    // ========================================================================
    // 🔒 LÓGICA ESPECÍFICA PROVIDER
    // ========================================================================

    private AuthResponse authenticateProvider(Provider provider, String rawPassword) {
        validateCredentials(provider, rawPassword);

        String token = jwtService.generateToken(provider);
        // String refreshToken = jwtService.generateRefreshToken(provider); // Futuro

        // Construir Status para Provider (Reglas de negocio estrictas)
        AuthResponse.AuthStatus status = AuthResponse.AuthStatus.builder()
                .isEmailVerified(provider.isEmailVerified())
                .isPhoneVerified(provider.isPhoneVerified())
                .onboardingComplete(provider.getOnboardingComplete()) // Vital para el Wizard
                .hasActivePlan(provider.isHasActivePlan())            // Vital para bloqueo por pago
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
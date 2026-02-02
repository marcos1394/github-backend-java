package com.quhealthy.auth_service.service;

import com.quhealthy.auth_service.dto.request.LoginRequest;
import com.quhealthy.auth_service.dto.response.AuthResponse;
import com.quhealthy.auth_service.model.Consumer;
import com.quhealthy.auth_service.model.Provider;
import com.quhealthy.auth_service.model.enums.Role;
import com.quhealthy.auth_service.model.enums.UserStatus;
import com.quhealthy.auth_service.repository.ConsumerRepository;
import com.quhealthy.auth_service.repository.ProviderRepository;
import com.quhealthy.auth_service.service.security.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock
    private ConsumerRepository consumerRepository;
    @Mock
    private ProviderRepository providerRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthenticationService authenticationService;

    // ========================================================================
    // ✅ TEST: LOGIN CONSUMER
    // ========================================================================

    @Test
    @DisplayName("Debe autenticar correctamente a un CONSUMER y devolver status por defecto")
    void authenticate_ShouldReturnToken_WhenConsumerIsValid() {
        // Arrange
        String email = "patient@test.com";
        String password = "password123";
        LoginRequest request = new LoginRequest(email, password);

        Consumer consumer = Consumer.builder()
                .id(1L)
                .email(email)
                .password("encodedPass")
                .firstName("Juan")
                .role(Role.CONSUMER)
                .status(UserStatus.ACTIVE)
                .isEmailVerified(true)
                .build();

        when(consumerRepository.findByEmail(email)).thenReturn(Optional.of(consumer));
        when(passwordEncoder.matches(password, consumer.getPassword())).thenReturn(true);
        when(jwtService.generateToken(any(Consumer.class))).thenReturn("mock-jwt-token-consumer");

        // Act
        AuthResponse response = authenticationService.authenticate(request);

        // Assert
        assertNotNull(response);
        assertEquals("mock-jwt-token-consumer", response.getToken());
        assertEquals("CONSUMER", response.getRole());

        // CORRECCIÓN: Usamos isOnboardingComplete() e isHasActivePlan()
        assertTrue(response.getStatus().isOnboardingComplete(), "Los pacientes siempre deben tener onboardingComplete en true");
        assertTrue(response.getStatus().isHasActivePlan(), "Los pacientes siempre tienen plan activo por defecto");
    }

    // ========================================================================
    // ✅ TEST: LOGIN PROVIDER
    // ========================================================================

    @Test
    @DisplayName("Debe autenticar correctamente a un PROVIDER y respetar sus flags de negocio")
    void authenticate_ShouldReturnToken_WhenProviderIsValid() {
        // Arrange
        String email = "doctor@test.com";
        String password = "password123";
        LoginRequest request = new LoginRequest(email, password);

        Provider provider = Provider.builder()
                .id(2L)
                .email(email)
                .password("encodedPass")
                .businessName("Clinica Test")
                .role(Role.PROVIDER)
                .status(UserStatus.ACTIVE)
                .onboardingComplete(true)
                .hasActivePlan(false)
                .build();

        when(consumerRepository.findByEmail(email)).thenReturn(Optional.empty());
        when(providerRepository.findByEmail(email)).thenReturn(Optional.of(provider));

        when(passwordEncoder.matches(password, provider.getPassword())).thenReturn(true);
        when(jwtService.generateToken(any(Provider.class))).thenReturn("mock-jwt-token-provider");

        // Act
        AuthResponse response = authenticationService.authenticate(request);

        // Assert
        assertNotNull(response);
        assertEquals("mock-jwt-token-provider", response.getToken());
        assertEquals("PROVIDER", response.getRole());

        // CORRECCIÓN: Usamos isOnboardingComplete() e isHasActivePlan()
        assertTrue(response.getStatus().isOnboardingComplete());
        assertFalse(response.getStatus().isHasActivePlan(), "Debe reflejar que el plan NO está activo");
    }

    // ========================================================================
    // ❌ TEST: ERRORES Y EXCEPCIONES
    // ========================================================================

    @Test
    @DisplayName("Debe lanzar BadCredentialsException si el usuario no existe en ninguna tabla")
    void authenticate_ShouldThrowException_WhenUserNotFound() {
        // Arrange
        LoginRequest request = new LoginRequest("ghost@test.com", "123");

        when(consumerRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(providerRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(BadCredentialsException.class, () -> authenticationService.authenticate(request));
    }

    @Test
    @DisplayName("Debe lanzar BadCredentialsException si la contraseña es incorrecta")
    void authenticate_ShouldThrowException_WhenPasswordIsInvalid() {
        // Arrange
        String email = "patient@test.com";
        LoginRequest request = new LoginRequest(email, "wrongPass");

        Consumer consumer = Consumer.builder().email(email).password("encodedPass").status(UserStatus.ACTIVE).build();

        when(consumerRepository.findByEmail(email)).thenReturn(Optional.of(consumer));
        when(passwordEncoder.matches(request.getPassword(), consumer.getPassword())).thenReturn(false);

        // Act & Assert
        assertThrows(BadCredentialsException.class, () -> authenticationService.authenticate(request));
    }

    @Test
    @DisplayName("Debe lanzar DisabledException si el usuario está suspendido o inactivo")
    void authenticate_ShouldThrowException_WhenUserIsDisabled() {
        // Arrange
        String email = "banned@test.com";
        LoginRequest request = new LoginRequest(email, "password123");

        Consumer consumer = Consumer.builder()
                .email(email)
                .password("encodedPass")
                .status(UserStatus.SUSPENDED)
                .build();

        when(consumerRepository.findByEmail(email)).thenReturn(Optional.of(consumer));

        // Act & Assert
        assertThrows(DisabledException.class, () -> authenticationService.authenticate(request));
    }
}
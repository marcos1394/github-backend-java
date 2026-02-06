package com.quhealthy.auth_service.service;

import com.quhealthy.auth_service.dto.request.LoginRequest;
import com.quhealthy.auth_service.dto.response.AuthResponse;
import com.quhealthy.auth_service.model.Consumer;
import com.quhealthy.auth_service.model.Plan;
import com.quhealthy.auth_service.model.Provider;
import com.quhealthy.auth_service.model.enums.UserStatus; // ✅ IMPORTANTE
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
import static org.mockito.ArgumentMatchers.anyMap;
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
    @DisplayName("Debe autenticar Consumer y retornar token cuando credenciales son válidas")
    void authenticate_ShouldReturnToken_WhenConsumerIsValid() {
        // Arrange
        LoginRequest request = new LoginRequest("patient@test.com", "password123");

        Consumer consumer = Consumer.builder()
                .email("patient@test.com")
                .password("encodedPass")
                .isEmailVerified(true)
                .firstName("Juan")
                // ✅ CORRECTO: Usamos el Enum Status (El default es ACTIVE, pero lo pongo explícito)
                .status(UserStatus.ACTIVE)
                .build();

        when(consumerRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(consumer));
        when(passwordEncoder.matches(request.getPassword(), consumer.getPassword())).thenReturn(true);
        when(jwtService.generateToken(anyMap(), any(Consumer.class))).thenReturn("jwt-token-xyz");

        // Act
        AuthResponse response = authenticationService.authenticate(request);

        // Assert
        assertNotNull(response);
        assertEquals("jwt-token-xyz", response.getToken());
        assertEquals("CONSUMER", response.getRole());

        verify(consumerRepository).findByEmail(request.getEmail());
        verify(jwtService).generateToken(anyMap(), any(Consumer.class));
    }

    // ========================================================================
    // ✅ TEST: LOGIN PROVIDER
    // ========================================================================

    @Test
    @DisplayName("Debe autenticar Provider y retornar token con claims enriquecidos")
    void authenticate_ShouldReturnToken_WhenProviderIsValid() {
        // Arrange
        LoginRequest request = new LoginRequest("doctor@test.com", "password123");
        Plan mockPlan = Plan.builder().id(2L).build();

        Provider provider = Provider.builder()
                .email("doctor@test.com")
                .businessName("Clinica Test")
                .password("encodedPass")
                .onboardingStatus("COMPLETED")
                .isEmailVerified(true)
                .kycStatus("PENDING")
                .hasActivePlan(true)
                .status(UserStatus.ACTIVE) // ✅ CORRECTO
                .plan(mockPlan)
                .build();

        when(consumerRepository.findByEmail(request.getEmail())).thenReturn(Optional.empty());
        when(providerRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(provider));
        when(passwordEncoder.matches(request.getPassword(), provider.getPassword())).thenReturn(true);
        when(jwtService.generateToken(anyMap(), any(Provider.class))).thenReturn("jwt-provider-token");

        // Act
        AuthResponse response = authenticationService.authenticate(request);

        // Assert
        assertNotNull(response);
        assertEquals("jwt-provider-token", response.getToken());
        assertEquals("PROVIDER", response.getRole());

        verify(jwtService).generateToken(anyMap(), any(Provider.class));
    }

    // ========================================================================
    // ❌ TEST: USUARIO NO ENCONTRADO
    // ========================================================================

    @Test
    @DisplayName("Debe lanzar BadCredentialsException si el usuario no existe")
    void authenticate_ShouldThrow_WhenUserNotFound() {
        LoginRequest request = new LoginRequest("ghost@test.com", "123");

        when(consumerRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(providerRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        assertThrows(BadCredentialsException.class, () -> authenticationService.authenticate(request));
    }

    // ========================================================================
    // ❌ TEST: PASSWORD INCORRECTO
    // ========================================================================

    @Test
    @DisplayName("Debe lanzar BadCredentialsException si el password es incorrecto")
    void authenticate_ShouldThrow_WhenPasswordInvalid() {
        LoginRequest request = new LoginRequest("patient@test.com", "wrongPass");
        Consumer consumer = Consumer.builder()
                .email("patient@test.com")
                .password("realPass")
                .status(UserStatus.ACTIVE) // ✅ CORRECTO: Usuario activo, password mal
                .build();

        when(consumerRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(consumer));
        when(passwordEncoder.matches(request.getPassword(), consumer.getPassword())).thenReturn(false);

        assertThrows(BadCredentialsException.class, () -> authenticationService.authenticate(request));

        verify(jwtService, never()).generateToken(any(), any());
    }

    // ========================================================================
    // ❌ TEST: CUENTA DESHABILITADA
    // ========================================================================

    @Test
    @DisplayName("Debe lanzar DisabledException si la cuenta está inactiva (SUSPENDED)")
    void authenticate_ShouldThrow_WhenAccountDisabled() {
        LoginRequest request = new LoginRequest("banned@test.com", "123");

        Consumer consumer = Consumer.builder()
                .email("banned@test.com")
                .password("pass")
                // ✅ CORRECCIÓN CLAVE: Usamos el estado SUSPENDED (o INACTIVE)
                // Esto hará que isActive() retorne false.
                .status(UserStatus.SUSPENDED)
                .build();

        when(consumerRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(consumer));

        assertThrows(DisabledException.class, () -> authenticationService.authenticate(request));

        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }
}
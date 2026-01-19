package com.quhealthy.auth_service.service;

import com.quhealthy.auth_service.dto.RegisterProviderRequest;
import com.quhealthy.auth_service.model.Plan;
import com.quhealthy.auth_service.model.Provider;
import com.quhealthy.auth_service.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class) // Habilita Mockito
class AuthServiceTest {

    @Mock private ProviderRepository providerRepository;
    @Mock private ProviderPlanRepository providerPlanRepository;
    @Mock private ProviderKYCRepository kycRepository;
    @Mock private ProviderLicenseRepository licenseRepository;
    @Mock private ProviderMarketplaceRepository marketplaceRepository;
    @Mock private ReferralRepository referralRepository;
    @Mock private PlanRepository planRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private NotificationService notificationService;

    @InjectMocks // Inyecta los mocks dentro del servicio real
    private AuthService authService;

    private RegisterProviderRequest request;

    @BeforeEach
    void setUp() {
        // Datos de prueba
        request = new RegisterProviderRequest();
        request.setName("Dr. House");
        request.setEmail("house@hospital.com");
        request.setPhone("5512345678");
        request.setPassword("vicodin123");
        request.setServiceType("health");
        request.setAcceptTerms(true);
    }

    @Test
    @DisplayName("✅ Registro Exitoso: Debería guardar provider y enviar email")
    void shouldRegisterProviderSuccessfully() {
        // 1. GIVEN (Preparación)
        // Simulamos que el email NO existe
        when(providerRepository.existsByEmail(request.getEmail())).thenReturn(false);
        // Simulamos que la contraseña se encripta
        when(passwordEncoder.encode(any())).thenReturn("hashed_password");
        // Simulamos que el Plan ID 5 existe
        Plan mockPlan = new Plan();
        mockPlan.setId(5L);
        mockPlan.setName("Free Plan");
        when(planRepository.findById(5L)).thenReturn(Optional.of(mockPlan));
        
        // Simulamos el guardado para que devuelva un ID
        when(providerRepository.save(any(Provider.class))).thenAnswer(invocation -> {
            Provider p = invocation.getArgument(0);
            p.setId(1L); // Simulamos que la BD le asignó ID 1
            return p;
        });

        // 2. WHEN (Ejecución)
        Provider result = authService.registerProvider(request);

        // 3. THEN (Verificación)
        assertNotNull(result);
        assertEquals("Dr. House", result.getName());
        assertEquals(1L, result.getId());

        // Verificamos que se llamó a los repositorios clave
        verify(providerRepository, times(1)).save(any(Provider.class)); // Se guardó el usuario
        verify(providerPlanRepository, times(1)).save(any()); // Se guardó el plan
        verify(kycRepository, times(1)).save(any()); // Se creó KYC vacío
        verify(marketplaceRepository, times(1)).save(any()); // Se creó la tienda

        // Verificamos que se intentó enviar el correo
        verify(notificationService, times(1)).sendVerificationEmail(eq("house@hospital.com"), eq("Dr. House"), anyString());
    }

    @Test
    @DisplayName("❌ Error: Debería fallar si el email ya existe")
    void shouldThrowExceptionIfEmailExists() {
        // 1. GIVEN
        when(providerRepository.existsByEmail(request.getEmail())).thenReturn(true);

        // 2. WHEN & THEN
        Exception exception = assertThrows(RuntimeException.class, () -> {
            authService.registerProvider(request);
        });

        assertEquals("El correo electrónico ya está registrado.", exception.getMessage());
        
        // Aseguramos que NUNCA se guardó nada
        verify(providerRepository, never()).save(any());
        verify(notificationService, never()).sendVerificationEmail(any(), any(), any());
    }
}
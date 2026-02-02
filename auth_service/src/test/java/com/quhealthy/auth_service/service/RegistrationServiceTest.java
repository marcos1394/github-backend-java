package com.quhealthy.auth_service.service;

import com.quhealthy.auth_service.dto.request.RegisterConsumerRequest;
import com.quhealthy.auth_service.dto.request.RegisterProviderRequest;
import com.quhealthy.auth_service.dto.response.ConsumerRegistrationResponse;
import com.quhealthy.auth_service.dto.response.ProviderRegistrationResponse;
import com.quhealthy.auth_service.model.Consumer;
import com.quhealthy.auth_service.model.Provider;
import com.quhealthy.auth_service.event.UserEvent;
import com.quhealthy.auth_service.event.UserEventPublisher; // ⚠️ CORRECCIÓN: Importamos la Interfaz
import com.quhealthy.auth_service.repository.ConsumerRepository;
import com.quhealthy.auth_service.repository.ProviderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

    @Mock
    private ConsumerRepository consumerRepository;
    @Mock
    private ProviderRepository providerRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    // ⚠️ CORRECCIÓN: Mockeamos la Interfaz (El contrato), no la implementación de Pub/Sub
    @Mock
    private UserEventPublisher eventPublisher;

    @InjectMocks
    private RegistrationService registrationService;

    // ========================================================================
    // ✅ TEST: REGISTRO CONSUMER
    // ========================================================================

    @Test
    @DisplayName("Debe registrar un Consumidor exitosamente cuando el email es único")
    void registerConsumer_ShouldSucceed_WhenEmailIsUnique() {
        // Arrange
        RegisterConsumerRequest request = RegisterConsumerRequest.builder()
                .email("new.patient@test.com")
                .password("SecurePass123!")
                .firstName("Juan")
                .lastName("Perez")
                .termsAccepted(true)
                .referralCode("FRIEND123")
                .build();

        when(consumerRepository.existsByEmail(anyString())).thenReturn(false);
        when(providerRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(request.getPassword())).thenReturn("hashed_secret");

        when(consumerRepository.save(any(Consumer.class))).thenAnswer(invocation -> {
            Consumer c = invocation.getArgument(0);
            c.setId(100L);
            return c;
        });

        // Act
        ConsumerRegistrationResponse response = registrationService.registerConsumer(request);

        // Assert
        assertNotNull(response);
        assertEquals(100L, response.getId());
        assertEquals("new.patient@test.com", response.getEmail());

        // Verificaciones:
        // Usamos el mock de la interfaz 'eventPublisher'
        ArgumentCaptor<UserEvent> eventCaptor = ArgumentCaptor.forClass(UserEvent.class);
        verify(eventPublisher, times(1)).publish(eventCaptor.capture());

        UserEvent capturedEvent = eventCaptor.getValue();
        assertEquals("USER_REGISTERED", capturedEvent.getEventType());
        assertEquals("CONSUMER", capturedEvent.getRole());

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = capturedEvent.getPayload();
        assertEquals("FRIEND123", payload.get("referralCode"));
        assertNotNull(payload.get("verificationToken"));
    }

    // ========================================================================
    // ✅ TEST: REGISTRO PROVIDER
    // ========================================================================

    @Test
    @DisplayName("Debe registrar un Proveedor exitosamente con estado pendiente de onboarding")
    void registerProvider_ShouldSucceed_WhenEmailIsUnique() {
        // Arrange
        RegisterProviderRequest request = RegisterProviderRequest.builder()
                .email("dr.house@test.com")
                .password("SecurePass123!")
                .firstName("Gregory")
                .lastName("House")
                .businessName("Diagnostics Dept")
                .parentCategoryId(5L)
                .termsAccepted(true)
                .build();

        when(consumerRepository.existsByEmail(anyString())).thenReturn(false);
        when(providerRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(request.getPassword())).thenReturn("hashed_secret");

        when(providerRepository.save(any(Provider.class))).thenAnswer(invocation -> {
            Provider p = invocation.getArgument(0);
            p.setId(200L);
            return p;
        });

        // Act
        ProviderRegistrationResponse response = registrationService.registerProvider(request);

        // Assert
        assertNotNull(response);
        assertEquals("dr.house@test.com", response.getEmail());

        ArgumentCaptor<Provider> providerCaptor = ArgumentCaptor.forClass(Provider.class);
        verify(providerRepository).save(providerCaptor.capture());

        Provider savedProvider = providerCaptor.getValue();
        assertFalse(savedProvider.getOnboardingComplete());
        assertNull(savedProvider.getCategory());
        assertEquals(5L, savedProvider.getParentCategoryId());

        // Verificar publicación en la interfaz
        verify(eventPublisher, times(1)).publish(any(UserEvent.class));
    }

    // ========================================================================
    // ❌ TEST: VALIDACIÓN DE DUPLICADOS
    // ========================================================================

    @Test
    @DisplayName("Debe lanzar excepción si el email ya existe como CONSUMER")
    void registerConsumer_ShouldThrow_WhenEmailExistsInConsumers() {
        // Arrange
        RegisterConsumerRequest request = new RegisterConsumerRequest("exist@test.com", "123", "F", "L", true, null, null, null);
        when(consumerRepository.existsByEmail(request.getEmail())).thenReturn(true);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> registrationService.registerConsumer(request));

        assertEquals("El correo electrónico ya está registrado como Paciente.", exception.getMessage());

        verify(consumerRepository, never()).save(any());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("Debe lanzar excepción si el email ya existe como PROVIDER (Cross-Table Check)")
    void registerConsumer_ShouldThrow_WhenEmailExistsInProviders() {
        // Arrange
        RegisterConsumerRequest request = new RegisterConsumerRequest("doctor@test.com", "123", "F", "L", true, null, null, null);

        when(consumerRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(providerRepository.existsByEmail(request.getEmail())).thenReturn(true);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> registrationService.registerConsumer(request));

        assertEquals("El correo electrónico ya está registrado como Profesional.", exception.getMessage());
    }
}
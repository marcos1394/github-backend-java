package com.quhealthy.onboarding_service.listener;

import com.quhealthy.onboarding_service.event.UserEvent;
import com.quhealthy.onboarding_service.model.ProviderOnboarding;
import com.quhealthy.onboarding_service.model.enums.OnboardingStatus;
import com.quhealthy.onboarding_service.repository.ProviderOnboardingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegistrationEventListenerTest {

    @Mock
    private ProviderOnboardingRepository onboardingRepository;

    @InjectMocks
    private RegistrationEventListener eventListener;

    private static final Long USER_ID = 101L;
    private static final String EMAIL = "doctor@test.com";

    @Test
    @DisplayName("Debe IGNORAR el evento si el rol NO es PROVIDER")
    void shouldIgnore_WhenRoleIsNotProvider() {
        // GIVEN
        UserEvent event = new UserEvent();
        event.setUserId(USER_ID);
        event.setEmail(EMAIL);
        event.setRole("PATIENT"); // 👈 Caso Paciente

        // WHEN
        eventListener.userRegisteredConsumer().accept(event);

        // THEN
        verify(onboardingRepository, never()).existsById(any());
        verify(onboardingRepository, never()).save(any());
    }

    @Test
    @DisplayName("Debe IGNORAR si el checklist YA EXISTE (Idempotencia)")
    void shouldIgnore_WhenChecklistAlreadyExists() {
        // GIVEN
        UserEvent event = new UserEvent();
        event.setUserId(USER_ID);
        event.setRole("PROVIDER");

        // Simulamos que ya existe en BD
        when(onboardingRepository.existsById(USER_ID)).thenReturn(true);

        // WHEN
        eventListener.userRegisteredConsumer().accept(event);

        // THEN
        verify(onboardingRepository, never()).save(any()); // No debe guardar nada nuevo
    }

    @Test
    @DisplayName("Debe CREAR checklist inicial si es PROVIDER nuevo (Happy Path)")
    void shouldCreateChecklist_WhenProviderIsNew() {
        // GIVEN
        UserEvent event = new UserEvent();
        event.setUserId(USER_ID);
        event.setEmail(EMAIL);
        event.setRole("PROVIDER");

        Map<String, Object> payload = new HashMap<>();
        payload.put("planId", 5); // Probamos con Integer
        event.setPayload(payload);

        when(onboardingRepository.existsById(USER_ID)).thenReturn(false);

        // WHEN
        eventListener.userRegisteredConsumer().accept(event);

        // THEN
        ArgumentCaptor<ProviderOnboarding> captor = ArgumentCaptor.forClass(ProviderOnboarding.class);
        verify(onboardingRepository).save(captor.capture());

        ProviderOnboarding saved = captor.getValue();
        assertThat(saved.getProviderId()).isEqualTo(USER_ID);
        assertThat(saved.getSelectedPlanId()).isEqualTo(5L);

        // Verificar estados iniciales alineados al proceso
        assertThat(saved.getProfileStatus()).isEqualTo(OnboardingStatus.PENDING);
        assertThat(saved.getKycStatus()).isEqualTo(OnboardingStatus.PENDING);
        assertThat(saved.getLicenseStatus()).isEqualTo(OnboardingStatus.PENDING);
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Debe manejar correctamente el parseo de PlanID cuando viene como String")
    void shouldParsePlanId_WhenStringProvided() {
        // GIVEN
        UserEvent event = new UserEvent();
        event.setUserId(USER_ID);
        event.setRole("PROVIDER");

        // A veces el JSON deserializa números como Strings
        event.setPayload(Map.of("planId", "99"));

        when(onboardingRepository.existsById(USER_ID)).thenReturn(false);

        // WHEN
        eventListener.userRegisteredConsumer().accept(event);

        // THEN
        ArgumentCaptor<ProviderOnboarding> captor = ArgumentCaptor.forClass(ProviderOnboarding.class);
        verify(onboardingRepository).save(captor.capture());

        assertThat(captor.getValue().getSelectedPlanId()).isEqualTo(99L);
    }
}
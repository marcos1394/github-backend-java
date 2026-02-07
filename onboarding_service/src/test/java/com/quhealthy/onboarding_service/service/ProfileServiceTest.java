package com.quhealthy.onboarding_service.service;

import com.quhealthy.onboarding_service.dto.request.UpdateProfileRequest;
import com.quhealthy.onboarding_service.event.OnboardingEventPublisher;
import com.quhealthy.onboarding_service.model.ProviderOnboarding;
import com.quhealthy.onboarding_service.model.ProviderProfile;
import com.quhealthy.onboarding_service.model.enums.OnboardingStatus;
import com.quhealthy.onboarding_service.repository.ProviderOnboardingRepository;
import com.quhealthy.onboarding_service.repository.ProviderProfileRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    @Mock
    private ProviderOnboardingRepository onboardingStatusRepository;

    @Mock
    private ProviderProfileRepository profileDataRepository;

    @Mock
    private OnboardingEventPublisher eventPublisher;

    @InjectMocks
    private ProfileService profileService;

    // Constantes para los tests
    private static final Long USER_ID = 100L;
    private static final String BUSINESS_NAME = "Consultorio Dr. House";
    private static final String BIO = "Especialista en diagnóstico diferencial.";

    // NOTA: Como el servicio usa UUID aleatorio, validaremos que empiece con esto:
    private static final String EXPECTED_SLUG_PREFIX = "consultorio-dr-house-";

    @Test
    @DisplayName("Debe CREAR un perfil nuevo y generar Slug si el usuario no existe")
    void updateProfile_WhenUserIsNew_ShouldCreateAndGenerateSlug() {
        // GIVEN
        UpdateProfileRequest request = createDummyRequest();

        // Simulamos que NO existe perfil ni status previo (Optional.empty)
        when(profileDataRepository.findById(USER_ID)).thenReturn(Optional.empty());
        when(onboardingStatusRepository.findById(USER_ID)).thenReturn(Optional.empty());

        // WHEN
        profileService.updateProfile(USER_ID, request);

        // THEN
        // 1. Verificar guardado de Perfil
        ArgumentCaptor<ProviderProfile> profileCaptor = ArgumentCaptor.forClass(ProviderProfile.class);
        verify(profileDataRepository).save(profileCaptor.capture());

        ProviderProfile savedProfile = profileCaptor.getValue();
        assertThat(savedProfile.getBusinessName()).isEqualTo(BUSINESS_NAME);

        // ✅ CORRECCIÓN SLUG: Usamos startsWith porque el sufijo es aleatorio
        assertThat(savedProfile.getSlug()).startsWith(EXPECTED_SLUG_PREFIX);
        assertThat(savedProfile.getWebsiteUrl()).isEqualTo("https://drhouse.com");
        assertThat(savedProfile.getProviderId()).isEqualTo(USER_ID);

        // 2. Verificar actualización de Status
        ArgumentCaptor<ProviderOnboarding> statusCaptor = ArgumentCaptor.forClass(ProviderOnboarding.class);
        verify(onboardingStatusRepository).save(statusCaptor.capture());

        ProviderOnboarding savedStatus = statusCaptor.getValue();
        assertThat(savedStatus.getProfileStatus()).isEqualTo(OnboardingStatus.COMPLETED);
        assertThat(savedStatus.getKycStatus()).isEqualTo(OnboardingStatus.PENDING); // Debe desbloquear el siguiente paso

        // 3. ✅ VERIFICACIÓN DE EVENTO RICO (Sincronización)
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> mapCaptor = ArgumentCaptor.forClass(Map.class);

        // Verificamos el nombre del evento actualizado: "PROFILE_COMPLETED"
        verify(eventPublisher).publishStepCompleted(eq(USER_ID), isNull(), eq("PROFILE_COMPLETED"), mapCaptor.capture());

        Map<String, Object> syncData = mapCaptor.getValue();
        assertThat(syncData).containsEntry("businessName", BUSINESS_NAME);
        assertThat(syncData).containsKey("slug"); // Debe llevar el slug
        assertThat(syncData).containsEntry("bio", BIO);
    }

    @Test
    @DisplayName("Debe ACTUALIZAR un perfil existente sin cambiar el Slug si ya tiene uno")
    void updateProfile_WhenUserExists_ShouldUpdateButKeepSlug() {
        // GIVEN
        UpdateProfileRequest request = createDummyRequest();
        request.setBusinessName("Nuevo Nombre Consultorio"); // Cambiamos nombre

        // Simulamos perfil existente con un slug viejo
        ProviderProfile existingProfile = ProviderProfile.builder()
                .providerId(USER_ID)
                .businessName("Viejo Nombre")
                .slug("viejo-nombre-slug") // Slug existente
                .build();

        // Simulamos status existente
        ProviderOnboarding existingStatus = ProviderOnboarding.builder()
                .providerId(USER_ID)
                .profileStatus(OnboardingStatus.PENDING)
                .kycStatus(OnboardingStatus.IN_PROGRESS) // Ya había avanzado
                .build();

        when(profileDataRepository.findById(USER_ID)).thenReturn(Optional.of(existingProfile));
        when(onboardingStatusRepository.findById(USER_ID)).thenReturn(Optional.of(existingStatus));

        // WHEN
        profileService.updateProfile(USER_ID, request);

        // THEN
        // 1. Verificar Perfil
        ArgumentCaptor<ProviderProfile> profileCaptor = ArgumentCaptor.forClass(ProviderProfile.class);
        verify(profileDataRepository).save(profileCaptor.capture());

        ProviderProfile updatedProfile = profileCaptor.getValue();
        assertThat(updatedProfile.getBusinessName()).isEqualTo("Nuevo Nombre Consultorio"); // Se actualizó el nombre

        // ✅ VALIDACIÓN CRÍTICA SEO: El slug NO cambió a pesar del cambio de nombre
        assertThat(updatedProfile.getSlug()).isEqualTo("viejo-nombre-slug");

        // 2. Verificar Status
        ArgumentCaptor<ProviderOnboarding> statusCaptor = ArgumentCaptor.forClass(ProviderOnboarding.class);
        verify(onboardingStatusRepository).save(statusCaptor.capture());

        ProviderOnboarding updatedStatus = statusCaptor.getValue();
        assertThat(updatedStatus.getProfileStatus()).isEqualTo(OnboardingStatus.COMPLETED);
        assertThat(updatedStatus.getKycStatus()).isEqualTo(OnboardingStatus.IN_PROGRESS);

        // 3. Verificar Evento de Sincronización
        // Aunque el slug no cambie, debemos enviar los datos nuevos (nombre nuevo + slug viejo)
        verify(eventPublisher).publishStepCompleted(eq(USER_ID), isNull(), eq("PROFILE_COMPLETED"), anyMap());
    }

    // --- Helper para crear Request Dummy ---
    private UpdateProfileRequest createDummyRequest() {
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setBusinessName(BUSINESS_NAME);
        request.setBio(BIO);
        request.setProfileImageUrl("http://img.com/me.jpg");
        request.setAddress("Calle Falsa 123");
        request.setLatitude(19.4326);
        request.setLongitude(-99.1332);
        request.setCategoryId(1L);
        request.setSubCategoryId(2L);
        request.setWebsiteUrl("https://drhouse.com");
        request.setContactPhone("5550000000");
        return request;
    }
}
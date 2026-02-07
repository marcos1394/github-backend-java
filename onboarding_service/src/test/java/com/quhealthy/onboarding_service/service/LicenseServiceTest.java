package com.quhealthy.onboarding_service.service;

import com.quhealthy.onboarding_service.dto.response.LicenseResponse;
import com.quhealthy.onboarding_service.event.OnboardingEventPublisher;
import com.quhealthy.onboarding_service.model.ProfessionalLicense;
import com.quhealthy.onboarding_service.model.ProviderOnboarding;
import com.quhealthy.onboarding_service.model.ProviderProfile;
import com.quhealthy.onboarding_service.model.enums.DocumentType;
import com.quhealthy.onboarding_service.model.enums.OnboardingStatus;
import com.quhealthy.onboarding_service.repository.ProfessionalLicenseRepository;
import com.quhealthy.onboarding_service.repository.ProviderOnboardingRepository;
import com.quhealthy.onboarding_service.repository.ProviderProfileRepository;
import com.quhealthy.onboarding_service.service.integration.GeminiKycService;
import com.quhealthy.onboarding_service.service.storage.StorageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LicenseServiceTest {

    @Mock
    private StorageService storageService;

    @Mock
    private GeminiKycService geminiKycService;

    @Mock
    private ProfessionalLicenseRepository licenseRepository;

    @Mock
    private ProviderProfileRepository profileRepository;

    @Mock
    private ProviderOnboardingRepository onboardingStatusRepository;

    @Mock
    private OnboardingEventPublisher eventPublisher;

    @InjectMocks
    private LicenseService licenseService;

    // Constantes de prueba
    private static final Long USER_ID = 200L;
    private static final String FILE_KEY = "providers/200/LICENSE-cedula.jpg";
    private static final String PROFILE_NAME = "Dr. Juan Pérez López";

    // =========================================================================
    // ✅ ESCENARIO 1: APROBACIÓN EXITOSA (HAPPY PATH)
    // =========================================================================

    @Test
    @DisplayName("Debe APROBAR la cédula si es válida y el nombre coincide con el perfil")
    void shouldApproveLicense_WhenValidAndNameMatches() {
        // GIVEN
        MockMultipartFile file = new MockMultipartFile("file", "cedula.jpg", "image/jpeg", new byte[]{1, 2});

        // 1. Mock Storage
        when(storageService.uploadFile(any(), eq(USER_ID), eq(DocumentType.PROFESSIONAL_LICENSE.name())))
                .thenReturn(FILE_KEY);
        when(storageService.getPresignedUrl(FILE_KEY)).thenReturn("http://url-temporal");

        // 2. Mock Gemini Response (Todo OK)
        Map<String, Object> aiResponse = Map.of(
                "es_legible", true,
                "documento_valido", true,
                "numero_cedula", "12345678",
                "nombre_titular", "JUAN PEREZ LOPEZ", // Coincide con el perfil
                "profesion", "MEDICO CIRUJANO",
                "institucion", "UNAM",
                "anio_registro", 2015
        );
        when(geminiKycService.extractLicenseData(any())).thenReturn(aiResponse);

        // 3. Mock Perfil (Para comparar nombre)
        ProviderProfile profile = ProviderProfile.builder().providerId(USER_ID).businessName(PROFILE_NAME).build();
        when(profileRepository.findById(USER_ID)).thenReturn(Optional.of(profile));

        // 4. Mock Repositorios (Usuario nuevo en este paso)
        when(licenseRepository.findByProviderId(USER_ID)).thenReturn(Optional.empty());
        when(onboardingStatusRepository.findById(USER_ID)).thenReturn(Optional.of(new ProviderOnboarding()));

        // WHEN
        LicenseResponse response = licenseService.uploadAndVerifyLicense(USER_ID, file);

        // THEN
        assertThat(response.getStatus()).isEqualTo("APPROVED");
        assertThat(response.getLicenseNumber()).isEqualTo("12345678");
        assertThat(response.getRejectionReason()).isNull();

        // Verificar que se guardó como verificado
        ArgumentCaptor<ProfessionalLicense> licenseCaptor = ArgumentCaptor.forClass(ProfessionalLicense.class);
        verify(licenseRepository).save(licenseCaptor.capture());
        assertThat(licenseCaptor.getValue().isVerified()).isTrue();
        assertThat(licenseCaptor.getValue().getYearIssued()).isEqualTo(2015);

        // Verificar estatus global COMPLETED
        ArgumentCaptor<ProviderOnboarding> statusCaptor = ArgumentCaptor.forClass(ProviderOnboarding.class);
        verify(onboardingStatusRepository).save(statusCaptor.capture());
        assertThat(statusCaptor.getValue().getLicenseStatus()).isEqualTo(OnboardingStatus.COMPLETED);

        // ✅ VERIFICACIÓN CRÍTICA: Evento LICENSE_COMPLETED
        verify(eventPublisher).publishStepCompleted(eq(USER_ID), isNull(), eq("LICENSE_COMPLETED"), anyMap());
    }

    // =========================================================================
    // ❌ ESCENARIO 2: DOCUMENTO ILEGIBLE
    // =========================================================================

    @Test
    @DisplayName("Debe RECHAZAR si Gemini indica que el documento es ilegible")
    void shouldReject_WhenDocumentIsIllegible() {
        // GIVEN
        MockMultipartFile file = new MockMultipartFile("file", "borroso.jpg", "image/jpeg", new byte[]{0});

        when(storageService.uploadFile(any(), any(), any())).thenReturn(FILE_KEY);

        // IA dice que no se lee
        Map<String, Object> aiResponse = Map.of("es_legible", false);
        when(geminiKycService.extractLicenseData(any())).thenReturn(aiResponse);

        // El servicio guarda el rechazo, así que necesitamos mocks de repos
        when(licenseRepository.findByProviderId(USER_ID)).thenReturn(Optional.empty());
        when(onboardingStatusRepository.findById(USER_ID)).thenReturn(Optional.of(new ProviderOnboarding()));

        // WHEN
        LicenseResponse response = licenseService.uploadAndVerifyLicense(USER_ID, file);

        // THEN
        assertThat(response.getStatus()).isEqualTo("REJECTED");
        assertThat(response.getRejectionReason()).contains("ilegible");

        // ✅ VERIFICACIÓN CRÍTICA: Evento LICENSE_REJECTED
        verify(eventPublisher).publishStepRejected(eq(USER_ID), isNull(), eq("LICENSE_REJECTED"), contains("ilegible"));
    }

    // =========================================================================
    // 🕵️ ESCENARIO 3: FRAUDE (NOMBRE NO COINCIDE)
    // =========================================================================

    @Test
    @DisplayName("Debe RECHAZAR si el nombre en la cédula no coincide con el perfil")
    void shouldReject_WhenNameDoesNotMatch() {
        // GIVEN
        MockMultipartFile file = new MockMultipartFile("file", "otro_doc.jpg", "image/jpeg", new byte[]{1});
        when(storageService.uploadFile(any(), any(), any())).thenReturn(FILE_KEY);

        // Perfil: Dr. Juan Pérez
        ProviderProfile profile = ProviderProfile.builder().providerId(USER_ID).businessName("Dr. Juan Pérez").build();
        when(profileRepository.findById(USER_ID)).thenReturn(Optional.of(profile));

        // Cédula: Nombre totalmente diferente
        Map<String, Object> aiResponse = Map.of(
                "es_legible", true,
                "documento_valido", true,
                "numero_cedula", "99999",
                "nombre_titular", "ROBERTO GOMEZ BOLAÑOS" // No coincide
        );
        when(geminiKycService.extractLicenseData(any())).thenReturn(aiResponse);

        when(licenseRepository.findByProviderId(USER_ID)).thenReturn(Optional.empty());
        when(onboardingStatusRepository.findById(USER_ID)).thenReturn(Optional.of(new ProviderOnboarding()));

        // WHEN
        LicenseResponse response = licenseService.uploadAndVerifyLicense(USER_ID, file);

        // THEN
        assertThat(response.getStatus()).isEqualTo("REJECTED");
        assertThat(response.getRejectionReason()).contains("no coincide");

        // Verificar que se guardó en BD como NO VERIFICADO
        ArgumentCaptor<ProfessionalLicense> captor = ArgumentCaptor.forClass(ProfessionalLicense.class);
        verify(licenseRepository).save(captor.capture());
        assertThat(captor.getValue().isVerified()).isFalse();

        // Verificar evento de rechazo
        verify(eventPublisher).publishStepRejected(eq(USER_ID), isNull(), eq("LICENSE_REJECTED"), anyString());
    }

    // =========================================================================
    // 🧩 ESCENARIO 4: ERROR DE DATOS
    // =========================================================================

    @Test
    @DisplayName("Debe lanzar excepción si no existe el Perfil del usuario")
    void shouldThrowException_WhenProfileNotFound() {
        // GIVEN
        MockMultipartFile file = new MockMultipartFile("file", "test.jpg", "image/jpeg", new byte[]{1});
        when(storageService.uploadFile(any(), any(), any())).thenReturn(FILE_KEY);

        // IA responde OK
        Map<String, Object> aiResponse = Map.of("es_legible", true, "documento_valido", true, "numero_cedula", "123");
        when(geminiKycService.extractLicenseData(any())).thenReturn(aiResponse);

        // PERO el repositorio de perfil devuelve Empty
        when(profileRepository.findById(USER_ID)).thenReturn(Optional.empty());

        // ACT & ASSERT
        assertThatThrownBy(() -> licenseService.uploadAndVerifyLicense(USER_ID, file))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Perfil de usuario no encontrado");

        // Verificar que NO se guardó nada en License ni Onboarding
        verify(licenseRepository, never()).save(any());
    }
}
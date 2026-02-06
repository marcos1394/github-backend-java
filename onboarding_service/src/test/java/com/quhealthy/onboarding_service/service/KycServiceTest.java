package com.quhealthy.onboarding_service.service;

import com.quhealthy.onboarding_service.dto.response.KycDocumentResponse;
import com.quhealthy.onboarding_service.event.OnboardingEventPublisher;
import com.quhealthy.onboarding_service.model.ProviderDocument;
import com.quhealthy.onboarding_service.model.ProviderOnboarding;
import com.quhealthy.onboarding_service.model.enums.DocumentType;
import com.quhealthy.onboarding_service.model.enums.OnboardingStatus;
import com.quhealthy.onboarding_service.model.enums.VerificationStatus;
import com.quhealthy.onboarding_service.repository.ProviderDocumentRepository;
import com.quhealthy.onboarding_service.repository.ProviderOnboardingRepository;
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

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KycServiceTest {

    @Mock
    private StorageService storageService;

    @Mock
    private GeminiKycService geminiKycService;

    @Mock
    private ProviderDocumentRepository documentRepository;

    @Mock
    private ProviderOnboardingRepository onboardingStatusRepository;

    @Mock
    private OnboardingEventPublisher eventPublisher;

    @InjectMocks
    private KycService kycService;

    // Constantes de prueba
    private static final Long USER_ID = 500L;
    private static final String INE_FILE_KEY = "providers/500/INE_FRONT-123.jpg";
    private static final String SELFIE_FILE_KEY = "providers/500/SELFIE-456.jpg";

    // =========================================================================
    // 📄 ESCENARIO 1: DOCUMENTO DE IDENTIDAD (OCR - HAPPY PATH)
    // =========================================================================

    @Test
    @DisplayName("Debe APROBAR documento (OCR) si Gemini confirma legibilidad y sin alteraciones")
    void shouldApproveDocument_WhenGeminiValidatesIt() {
        // GIVEN
        MockMultipartFile file = new MockMultipartFile("file", "ine.jpg", "image/jpeg", new byte[]{10});

        // 1. Mock Storage
        when(storageService.uploadFile(any(), eq(USER_ID), anyString())).thenReturn(INE_FILE_KEY);
        when(storageService.getPresignedUrl(INE_FILE_KEY)).thenReturn("http://signed-url");

        // 2. Mock Gemini (OCR Exitoso)
        Map<String, Object> geminiResponse = Map.of(
                "es_legible", true,
                "parece_alterado", false,
                "curp", "ABCD123456",
                "nombre_completo", "Juan Perez"
        );
        when(geminiKycService.extractIdentityData(any(), eq("INE_FRONT"))).thenReturn(geminiResponse);

        // 3. Mock Repositorios
        when(documentRepository.findByProviderIdAndDocumentType(USER_ID, DocumentType.INE_FRONT)).thenReturn(Optional.empty());
        when(onboardingStatusRepository.findById(USER_ID)).thenReturn(Optional.of(new ProviderOnboarding()));
        when(documentRepository.findAllByProviderId(USER_ID)).thenReturn(Collections.emptyList());

        // WHEN
        KycDocumentResponse response = kycService.uploadAndVerifyDocument(USER_ID, file, DocumentType.INE_FRONT);

        // THEN
        assertThat(response.getVerificationStatus()).isEqualTo("APPROVED");
        assertThat(response.getRejectionReason()).isNull();

        ArgumentCaptor<ProviderDocument> docCaptor = ArgumentCaptor.forClass(ProviderDocument.class);
        verify(documentRepository).save(docCaptor.capture());
        assertThat(docCaptor.getValue().getVerificationStatus()).isEqualTo(VerificationStatus.APPROVED);
    }

    // =========================================================================
    // ❌ ESCENARIO 2: DOCUMENTO DE IDENTIDAD (OCR - RECHAZOS)
    // =========================================================================

    @Test
    @DisplayName("Debe RECHAZAR documento y notificar si es ILEGIBLE")
    void shouldRejectDocument_WhenIllegible() {
        // GIVEN
        MockMultipartFile file = new MockMultipartFile("file", "blurry.jpg", "image/jpeg", new byte[]{0});
        when(storageService.uploadFile(any(), any(), any())).thenReturn(INE_FILE_KEY);

        // Mock Gemini (Ilegible)
        when(geminiKycService.extractIdentityData(any(), any())).thenReturn(Map.of("es_legible", false));

        when(documentRepository.findByProviderIdAndDocumentType(any(), any())).thenReturn(Optional.empty());
        when(onboardingStatusRepository.findById(any())).thenReturn(Optional.of(new ProviderOnboarding()));

        // WHEN
        KycDocumentResponse response = kycService.uploadAndVerifyDocument(USER_ID, file, DocumentType.INE_FRONT);

        // THEN
        assertThat(response.getVerificationStatus()).isEqualTo("REJECTED");
        assertThat(response.getRejectionReason()).contains("no es legible");

        // Verificamos que contenga "legible" (para matchear con "no es legible")
        verify(eventPublisher).publishStepRejected(eq(USER_ID), isNull(), eq("KYC_DOCUMENT"), contains("legible"));
    }

    @Test
    @DisplayName("Debe RECHAZAR documento y notificar si parece ALTERADO")
    void shouldRejectDocument_WhenAltered() {
        // GIVEN
        MockMultipartFile file = new MockMultipartFile("file", "fake.jpg", "image/jpeg", new byte[]{0});
        when(storageService.uploadFile(any(), any(), any())).thenReturn(INE_FILE_KEY);

        // Mock Gemini
        Map<String, Object> resp = Map.of("es_legible", true, "parece_alterado", true);
        when(geminiKycService.extractIdentityData(any(), any())).thenReturn(resp);

        when(documentRepository.findByProviderIdAndDocumentType(any(), any())).thenReturn(Optional.empty());
        when(onboardingStatusRepository.findById(any())).thenReturn(Optional.of(new ProviderOnboarding()));

        // WHEN
        KycDocumentResponse response = kycService.uploadAndVerifyDocument(USER_ID, file, DocumentType.INE_FRONT);

        // THEN
        assertThat(response.getVerificationStatus()).isEqualTo("REJECTED");
        verify(eventPublisher).publishStepRejected(eq(USER_ID), isNull(), eq("KYC_SECURITY"), contains("alteraciones"));
    }

    // =========================================================================
    // 📸 ESCENARIO 3: BIOMETRÍA (SELFIE - HAPPY PATH)
    // =========================================================================

    @Test
    @DisplayName("Debe APROBAR Selfie y COMPLETAR Onboarding si Face Match es exitoso")
    void shouldApproveSelfie_AndCompleteOnboarding() {
        // GIVEN
        MockMultipartFile selfieFile = new MockMultipartFile("file", "selfie.jpg", "image/jpeg", new byte[]{99});

        // 1. Mock Upload Selfie
        when(storageService.uploadFile(any(), eq(USER_ID), eq("SELFIE"))).thenReturn(SELFIE_FILE_KEY);

        // 2. Mock Recuperación de INE (Pre-requisito)
        ProviderDocument approvedIne = ProviderDocument.builder()
                .documentType(DocumentType.INE_FRONT)
                .verificationStatus(VerificationStatus.APPROVED)
                .fileKey(INE_FILE_KEY)
                .build();
        when(documentRepository.findByProviderIdAndDocumentType(USER_ID, DocumentType.INE_FRONT)).thenReturn(Optional.of(approvedIne));

        // 3. Mock Descarga de bytes de INE para comparar
        when(storageService.getFileBytes(INE_FILE_KEY)).thenReturn(new byte[]{1, 2, 3});

        // 4. Mock Gemini Biometría (Éxito)
        Map<String, Object> bioResult = Map.of("is_same_person", true, "liveness_check", "PASSED");
        when(geminiKycService.verifyBiometricMatch(any(), any())).thenReturn(bioResult);

        // 5. Mocks Repositorios (Status Global y Rechazos)
        when(onboardingStatusRepository.findById(USER_ID)).thenReturn(Optional.of(new ProviderOnboarding()));
        when(documentRepository.findAllByProviderId(USER_ID)).thenReturn(Collections.emptyList());

        // 6. ✅ CORRECCIÓN DEFINITIVA: Retornos Encadenados
        // Preparamos la respuesta de "Selfie Aprobada" para cuando el sistema verifique si terminó.
        ProviderDocument approvedSelfie = ProviderDocument.builder().verificationStatus(VerificationStatus.APPROVED).build();

        // Configuración INTELIGENTE:
        // - 1ra llamada (Verificar si existe antes de subir): Retorna EMPTY
        // - 2da llamada (Verificar si ya completó onboarding): Retorna APPROVED
        when(documentRepository.findByProviderIdAndDocumentType(USER_ID, DocumentType.SELFIE))
                .thenReturn(Optional.empty())        // 1. Para la validación inicial
                .thenReturn(Optional.of(approvedSelfie)); // 2. Para la validación final (si se llama)

        // ⚠️ Nota: Hemos ELIMINADO los bloques 'lenient().doReturn(...)' del final
        // porque causaban el error UnnecessaryStubbingException.

        // WHEN
        KycDocumentResponse response = kycService.uploadAndVerifyDocument(USER_ID, selfieFile, DocumentType.SELFIE);

        // THEN
        assertThat(response.getVerificationStatus()).isEqualTo("APPROVED");

        ArgumentCaptor<ProviderOnboarding> statusCaptor = ArgumentCaptor.forClass(ProviderOnboarding.class);
        verify(onboardingStatusRepository).save(statusCaptor.capture());
        assertThat(statusCaptor.getValue().getKycStatus()).isEqualTo(OnboardingStatus.COMPLETED);

        verify(eventPublisher).publishStepCompleted(eq(USER_ID), isNull(), eq("KYC"), anyMap());
    }

    // =========================================================================
    // 🎭 ESCENARIO 4: BIOMETRÍA (SELFIE - FALLOS)
    // =========================================================================

    @Test
    @DisplayName("Debe RECHAZAR Selfie si Face Match falla")
    void shouldRejectSelfie_WhenFaceMatchFails() {
        // GIVEN
        MockMultipartFile file = new MockMultipartFile("file", "selfie.jpg", "image/jpeg", new byte[]{0});
        when(storageService.uploadFile(any(), any(), any())).thenReturn(SELFIE_FILE_KEY);

        // Pre-requisito OK
        ProviderDocument ineDoc = ProviderDocument.builder().verificationStatus(VerificationStatus.APPROVED).fileKey(INE_FILE_KEY).build();
        when(documentRepository.findByProviderIdAndDocumentType(USER_ID, DocumentType.INE_FRONT)).thenReturn(Optional.of(ineDoc));
        when(storageService.getFileBytes(INE_FILE_KEY)).thenReturn(new byte[]{1});

        // Gemini Biometría Fallida
        Map<String, Object> bioResult = Map.of("is_same_person", false, "liveness_check", "FAILED");
        when(geminiKycService.verifyBiometricMatch(any(), any())).thenReturn(bioResult);

        when(onboardingStatusRepository.findById(any())).thenReturn(Optional.of(new ProviderOnboarding()));
        when(documentRepository.findByProviderIdAndDocumentType(USER_ID, DocumentType.SELFIE)).thenReturn(Optional.empty());

        // WHEN
        KycDocumentResponse response = kycService.uploadAndVerifyDocument(USER_ID, file, DocumentType.SELFIE);

        // THEN
        assertThat(response.getVerificationStatus()).isEqualTo("REJECTED");
        verify(eventPublisher).publishStepRejected(eq(USER_ID), isNull(), eq("KYC_BIOMETRICS"), anyString());
    }

    @Test
    @DisplayName("Debe manejar excepción y RECHAZAR si intenta subir Selfie SIN tener INE aprobada")
    void shouldCatchExceptionAndReject_WhenNoIdFound() {
        // GIVEN
        MockMultipartFile file = new MockMultipartFile("file", "selfie.jpg", "image/jpeg", new byte[]{0});
        when(storageService.uploadFile(any(), any(), any())).thenReturn(SELFIE_FILE_KEY);

        // Sin documentos previos
        when(documentRepository.findByProviderIdAndDocumentType(USER_ID, DocumentType.INE_FRONT)).thenReturn(Optional.empty());
        when(documentRepository.findByProviderIdAndDocumentType(USER_ID, DocumentType.PASSPORT)).thenReturn(Optional.empty());

        when(documentRepository.findByProviderIdAndDocumentType(USER_ID, DocumentType.SELFIE)).thenReturn(Optional.empty());
        when(onboardingStatusRepository.findById(any())).thenReturn(Optional.of(new ProviderOnboarding()));

        // WHEN
        KycDocumentResponse response = kycService.uploadAndVerifyDocument(USER_ID, file, DocumentType.SELFIE);

        // THEN
        assertThat(response.getVerificationStatus()).isEqualTo("REJECTED");
        assertThat(response.getRejectionReason()).contains("Debes subir y aprobar tu identificación");
        verifyNoInteractions(geminiKycService);
    }
}
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

        when(storageService.uploadFile(any(), eq(USER_ID), anyString())).thenReturn(INE_FILE_KEY);
        when(storageService.getPresignedUrl(INE_FILE_KEY)).thenReturn("http://signed-url");

        Map<String, Object> geminiResponse = Map.of(
                "es_legible", true,
                "parece_alterado", false,
                "curp", "ABCD123456",
                "nombre_completo", "Juan Perez"
        );
        when(geminiKycService.extractIdentityData(any(), eq("INE_FRONT"))).thenReturn(geminiResponse);

        // Mocks para updateGlobalKycStatus
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

        when(geminiKycService.extractIdentityData(any(), any())).thenReturn(Map.of("es_legible", false));

        when(documentRepository.findByProviderIdAndDocumentType(any(), any())).thenReturn(Optional.empty());
        when(onboardingStatusRepository.findById(any())).thenReturn(Optional.of(new ProviderOnboarding()));

        // WHEN
        KycDocumentResponse response = kycService.uploadAndVerifyDocument(USER_ID, file, DocumentType.INE_FRONT);

        // THEN
        assertThat(response.getVerificationStatus()).isEqualTo("REJECTED");
        assertThat(response.getRejectionReason()).contains("legible");

        verify(eventPublisher).publishStepRejected(eq(USER_ID), isNull(), eq("KYC_DOCUMENT"), contains("legible"));
    }

    // =========================================================================
    // 📸 ESCENARIO 3: BIOMETRÍA (SELFIE - HAPPY PATH)
    // =========================================================================

    @Test
    @DisplayName("Debe APROBAR Selfie y COMPLETAR Onboarding si Face Match es exitoso")
    void shouldApproveSelfie_AndCompleteOnboarding() {
        // GIVEN
        MockMultipartFile selfieFile = new MockMultipartFile("file", "selfie.jpg", "image/jpeg", new byte[]{99});

        // 1. Upload
        when(storageService.uploadFile(any(), eq(USER_ID), eq("SELFIE"))).thenReturn(SELFIE_FILE_KEY);

        // 2. Pre-requisito: INE Aprobada
        ProviderDocument approvedIne = ProviderDocument.builder()
                .documentType(DocumentType.INE_FRONT)
                .verificationStatus(VerificationStatus.APPROVED)
                .fileKey(INE_FILE_KEY)
                .build();

        // 3. Documento de Selfie (Para la lógica de guardado)
        ProviderDocument approvedSelfie = ProviderDocument.builder()
                .documentType(DocumentType.SELFIE)
                .verificationStatus(VerificationStatus.APPROVED)
                .build();

        // ⚠️ CONFIGURACIÓN MOCKITO CRÍTICA:
        // KycService llama a `findByProviderIdAndDocumentType` varias veces:
        // 1. Para verificar pre-requisito (INE) -> Retorna approvedIne
        // 2. Para ver si ya existía selfie (Guardado) -> Retorna empty (primera vez)
        // 3. Dentro de `updateGlobalKycStatus` para verificar si INE está OK -> Retorna approvedIne
        // 4. Dentro de `updateGlobalKycStatus` para verificar si Selfie está OK -> Retorna approvedSelfie (simulando que se acaba de guardar)

        when(documentRepository.findByProviderIdAndDocumentType(USER_ID, DocumentType.INE_FRONT))
                .thenReturn(Optional.of(approvedIne));

        // Para Passport (retorna empty)
        lenient().when(documentRepository.findByProviderIdAndDocumentType(USER_ID, DocumentType.PASSPORT))
                .thenReturn(Optional.empty());

        when(documentRepository.findByProviderIdAndDocumentType(USER_ID, DocumentType.SELFIE))
                .thenReturn(Optional.empty())       // Llamada 1 (Guardado inicial)
                .thenReturn(Optional.of(approvedSelfie)); // Llamada 2 (Validación final)

        // 4. Mock Storage Bytes
        when(storageService.getFileBytes(INE_FILE_KEY)).thenReturn(new byte[]{1, 2, 3});

        // 5. Mock Gemini Exitoso
        Map<String, Object> bioResult = Map.of("is_same_person", true, "liveness_check", "PASSED");
        when(geminiKycService.verifyBiometricMatch(any(), any())).thenReturn(bioResult);

        // 6. Repos
        when(onboardingStatusRepository.findById(USER_ID)).thenReturn(Optional.of(new ProviderOnboarding()));
        when(documentRepository.findAllByProviderId(USER_ID)).thenReturn(Collections.emptyList());

        // WHEN
        KycDocumentResponse response = kycService.uploadAndVerifyDocument(USER_ID, selfieFile, DocumentType.SELFIE);

        // THEN
        assertThat(response.getVerificationStatus()).isEqualTo("APPROVED");

        // 1. Verificar estado en BD local
        ArgumentCaptor<ProviderOnboarding> statusCaptor = ArgumentCaptor.forClass(ProviderOnboarding.class);
        verify(onboardingStatusRepository).save(statusCaptor.capture());
        assertThat(statusCaptor.getValue().getKycStatus()).isEqualTo(OnboardingStatus.COMPLETED);

        // 2. ✅ VERIFICACIÓN CRÍTICA: Evento KYC_COMPLETED con datos correctos
        ArgumentCaptor<Map<String, Object>> mapCaptor = ArgumentCaptor.forClass(Map.class);
        verify(eventPublisher).publishStepCompleted(eq(USER_ID), isNull(), eq("KYC_COMPLETED"), mapCaptor.capture());

        Map<String, Object> eventData = mapCaptor.getValue();
        assertThat(eventData.get("kycVerified")).isEqualTo(true);
        assertThat(eventData.get("finalStatus")).isEqualTo("APPROVED");
    }

    // =========================================================================
    // 🎭 ESCENARIO 4: FALLOS EN SELFIE
    // =========================================================================

    @Test
    @DisplayName("Debe RECHAZAR Selfie si Face Match falla")
    void shouldRejectSelfie_WhenFaceMatchFails() {
        MockMultipartFile file = new MockMultipartFile("file", "selfie.jpg", "image/jpeg", new byte[]{0});
        when(storageService.uploadFile(any(), any(), any())).thenReturn(SELFIE_FILE_KEY);

        ProviderDocument ineDoc = ProviderDocument.builder().verificationStatus(VerificationStatus.APPROVED).fileKey(INE_FILE_KEY).build();
        when(documentRepository.findByProviderIdAndDocumentType(USER_ID, DocumentType.INE_FRONT)).thenReturn(Optional.of(ineDoc));
        when(storageService.getFileBytes(INE_FILE_KEY)).thenReturn(new byte[]{1});

        Map<String, Object> bioResult = Map.of("is_same_person", false, "liveness_check", "FAILED");
        when(geminiKycService.verifyBiometricMatch(any(), any())).thenReturn(bioResult);

        when(onboardingStatusRepository.findById(any())).thenReturn(Optional.of(new ProviderOnboarding()));
        when(documentRepository.findByProviderIdAndDocumentType(USER_ID, DocumentType.SELFIE)).thenReturn(Optional.empty());

        KycDocumentResponse response = kycService.uploadAndVerifyDocument(USER_ID, file, DocumentType.SELFIE);

        assertThat(response.getVerificationStatus()).isEqualTo("REJECTED");
        verify(eventPublisher).publishStepRejected(eq(USER_ID), isNull(), eq("KYC_BIOMETRICS"), anyString());
    }

    @Test
    @DisplayName("Debe RECHAZAR si intenta subir Selfie SIN tener INE aprobada")
    void shouldCatchExceptionAndReject_WhenNoIdFound() {
        MockMultipartFile file = new MockMultipartFile("file", "selfie.jpg", "image/jpeg", new byte[]{0});
        when(storageService.uploadFile(any(), any(), any())).thenReturn(SELFIE_FILE_KEY);

        when(documentRepository.findByProviderIdAndDocumentType(USER_ID, DocumentType.INE_FRONT)).thenReturn(Optional.empty());
        when(documentRepository.findByProviderIdAndDocumentType(USER_ID, DocumentType.PASSPORT)).thenReturn(Optional.empty());

        // Mocks adicionales para evitar NullPointers en flujos alternos
        when(documentRepository.findByProviderIdAndDocumentType(USER_ID, DocumentType.SELFIE)).thenReturn(Optional.empty());
        when(onboardingStatusRepository.findById(any())).thenReturn(Optional.of(new ProviderOnboarding()));

        KycDocumentResponse response = kycService.uploadAndVerifyDocument(USER_ID, file, DocumentType.SELFIE);

        assertThat(response.getVerificationStatus()).isEqualTo("REJECTED");
        assertThat(response.getRejectionReason()).contains("Debes aprobar tu identificación");
        verifyNoInteractions(geminiKycService);
    }
}
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class KycService {

    private final StorageService storageService;
    private final GeminiKycService geminiKycService;
    private final ProviderDocumentRepository documentRepository;
    private final ProviderOnboardingRepository onboardingStatusRepository;
    private final OnboardingEventPublisher eventPublisher;

    @Transactional
    public KycDocumentResponse uploadAndVerifyDocument(Long userId, MultipartFile file, DocumentType docType) {
        log.info("Procesando documento tipo {} para usuario {}", docType, userId);

        // 1. Subir archivo
        String fileKey = storageService.uploadFile(file, userId, docType.name());

        Map<String, Object> iaResult;
        VerificationStatus docStatus = VerificationStatus.PENDING;
        String rejectionReason = null;

        // 2. Lógica IA (Sin cambios, tu lógica es sólida)
        if (docType == DocumentType.SELFIE) {
            try {
                iaResult = processLivenessCheck(userId, file);
                Boolean isSamePerson = (Boolean) iaResult.getOrDefault("is_same_person", false);
                String liveness = (String) iaResult.getOrDefault("liveness_check", "FAILED");

                if (Boolean.TRUE.equals(isSamePerson) && "PASSED".equalsIgnoreCase(liveness)) {
                    docStatus = VerificationStatus.APPROVED;
                } else {
                    docStatus = VerificationStatus.REJECTED;
                    rejectionReason = "La prueba de vida falló: El rostro no coincide o no es una foto en vivo.";
                    // Notificar rechazo específico
                    eventPublisher.publishStepRejected(userId, null, "KYC_BIOMETRICS", rejectionReason);
                }
            } catch (Exception e) {
                docStatus = VerificationStatus.REJECTED;
                rejectionReason = e.getMessage();
                iaResult = Map.of("error", e.getMessage());
            }
        } else {
            iaResult = geminiKycService.extractIdentityData(file, docType.name());
            Boolean esLegible = (Boolean) iaResult.getOrDefault("es_legible", false);
            Boolean pareceAlterado = (Boolean) iaResult.getOrDefault("parece_alterado", false);

            if (Boolean.FALSE.equals(esLegible)) {
                docStatus = VerificationStatus.REJECTED;
                rejectionReason = "El documento no es legible.";
                eventPublisher.publishStepRejected(userId, null, "KYC_DOCUMENT", rejectionReason);
            } else if (Boolean.TRUE.equals(pareceAlterado)) {
                docStatus = VerificationStatus.REJECTED;
                rejectionReason = "Posible alteración digital detectada.";
                eventPublisher.publishStepRejected(userId, null, "KYC_SECURITY", rejectionReason);
            } else {
                docStatus = VerificationStatus.APPROVED;
            }
        }

        // 3. Guardar Documento
        ProviderDocument doc = documentRepository.findByProviderIdAndDocumentType(userId, docType)
                .orElse(ProviderDocument.builder().providerId(userId).documentType(docType).build());

        doc.setFileKey(fileKey);
        doc.setExtractedData(iaResult);
        doc.setVerificationStatus(docStatus);
        doc.setRejectionReason(rejectionReason);
        doc.setVerifiedAt(LocalDateTime.now());
        documentRepository.save(doc);

        // 4. Actualizar Estado Global (CRÍTICO PARA EL TOKEN)
        updateGlobalKycStatus(userId);

        // 5. Respuesta
        return KycDocumentResponse.builder()
                .documentType(docType.name())
                .verificationStatus(docStatus.name())
                .rejectionReason(rejectionReason)
                .extractedData(iaResult)
                .fileUrl(storageService.getPresignedUrl(fileKey))
                .lastUpdated(LocalDateTime.now().toString())
                .build();
    }

    private Map<String, Object> processLivenessCheck(Long userId, MultipartFile selfieFile) {
        ProviderDocument refDoc = documentRepository.findByProviderIdAndDocumentType(userId, DocumentType.INE_FRONT)
                .or(() -> documentRepository.findByProviderIdAndDocumentType(userId, DocumentType.PASSPORT))
                .orElseThrow(() -> new IllegalArgumentException("Debes aprobar tu identificación oficial antes de la selfie."));

        if (refDoc.getVerificationStatus() != VerificationStatus.APPROVED) {
            throw new IllegalArgumentException("Tu identificación no está aprobada. No puedes subir selfie aún.");
        }
        byte[] idImageBytes = storageService.getFileBytes(refDoc.getFileKey());
        return geminiKycService.verifyBiometricMatch(selfieFile, idImageBytes);
    }

    /**
     * Calcula el estado global y emite el evento para que Auth Service se entere.
     */
    private void updateGlobalKycStatus(Long userId) {
        boolean hasRejectedDocs = documentRepository.findAllByProviderId(userId).stream()
                .anyMatch(d -> d.getVerificationStatus() == VerificationStatus.REJECTED);

        ProviderOnboarding onboarding = onboardingStatusRepository.findById(userId)
                .orElse(ProviderOnboarding.builder().providerId(userId).build());

        // Variable para determinar qué string mandar al Auth Service ("APPROVED", "REJECTED", "PENDING")
        String authKycStatus;

        if (hasRejectedDocs) {
            onboarding.setKycStatus(OnboardingStatus.ACTION_REQUIRED);
            authKycStatus = "REJECTED"; // O "ACTION_REQUIRED" si Auth lo soporta
        } else {
            boolean hasId = checkApproved(userId, DocumentType.INE_FRONT) || checkApproved(userId, DocumentType.PASSPORT);
            boolean hasSelfie = checkApproved(userId, DocumentType.SELFIE);

            if (hasId && hasSelfie) {
                onboarding.setKycStatus(OnboardingStatus.COMPLETED);
                authKycStatus = "APPROVED"; // ✅ ¡Esto es lo que busca el Token!

                // 🔥 EVENTO CRÍTICO:
                // Notificamos que el usuario ya es confiable.
                // El consumidor de este evento (Auth Service o Worker) debe hacer:
                // UPDATE providers SET kyc_status = 'APPROVED' WHERE id = userId
                Map<String, Object> eventData = new HashMap<>();
                eventData.put("kycVerified", true);
                eventData.put("finalStatus", authKycStatus);

                eventPublisher.publishStepCompleted(userId, null, "KYC_COMPLETED", eventData);

            } else {
                onboarding.setKycStatus(OnboardingStatus.IN_PROGRESS);
                authKycStatus = "PENDING";
            }
        }
        onboardingStatusRepository.save(onboarding);
    }

    // Helper simple
    private boolean checkApproved(Long userId, DocumentType type) {
        return documentRepository.findByProviderIdAndDocumentType(userId, type)
                .map(d -> d.getVerificationStatus() == VerificationStatus.APPROVED)
                .orElse(false);
    }
}
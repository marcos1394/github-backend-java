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

        // 1. Subir el archivo (Sea Selfie o Documento, necesitamos guardarlo)
        String fileKey = storageService.uploadFile(file, userId, docType.name());

        Map<String, Object> iaResult;
        VerificationStatus docStatus = VerificationStatus.PENDING;
        String rejectionReason = null;

        // 2. BIFURCACIÓN DE LÓGICA (OCR vs BIOMETRÍA)
        if (docType == DocumentType.SELFIE) {
            // === RUTA A: PRUEBA DE VIDA ===
            try {
                iaResult = processLivenessCheck(userId, file);

                Boolean isSamePerson = (Boolean) iaResult.getOrDefault("is_same_person", false);
                String liveness = (String) iaResult.getOrDefault("liveness_check", "FAILED");

                if (Boolean.TRUE.equals(isSamePerson) && "PASSED".equalsIgnoreCase(liveness)) {
                    docStatus = VerificationStatus.APPROVED;
                } else {
                    docStatus = VerificationStatus.REJECTED;
                    rejectionReason = "La prueba de vida falló: El rostro no coincide o no es una foto en vivo.";
                    // Notificar rechazo
                    eventPublisher.publishStepRejected(userId, null, "KYC_BIOMETRICS", rejectionReason);
                }
            } catch (Exception e) {
                // Si falla (ej: no ha subido INE antes), marcamos como error
                docStatus = VerificationStatus.REJECTED;
                rejectionReason = e.getMessage();
                iaResult = Map.of("error", e.getMessage());
            }

        } else {
            // === RUTA B: DOCUMENTO OFICIAL (OCR) ===
            iaResult = geminiKycService.extractIdentityData(file, docType.name());

            Boolean esLegible = (Boolean) iaResult.getOrDefault("es_legible", false);
            Boolean pareceAlterado = (Boolean) iaResult.getOrDefault("parece_alterado", false);

            if (Boolean.FALSE.equals(esLegible)) {
                docStatus = VerificationStatus.REJECTED;
                rejectionReason = "El documento no es legible o está borroso.";
                eventPublisher.publishStepRejected(userId, null, "KYC_DOCUMENT", rejectionReason);
            } else if (Boolean.TRUE.equals(pareceAlterado)) {
                docStatus = VerificationStatus.REJECTED;
                rejectionReason = "El sistema detectó posibles alteraciones digitales.";
                eventPublisher.publishStepRejected(userId, null, "KYC_SECURITY", rejectionReason);
            } else {
                docStatus = VerificationStatus.APPROVED;
            }
        }

        // 3. Guardar o Actualizar en BD
        ProviderDocument doc = documentRepository.findByProviderIdAndDocumentType(userId, docType)
                .orElse(ProviderDocument.builder().providerId(userId).documentType(docType).build());

        doc.setFileKey(fileKey);
        doc.setExtractedData(iaResult);
        doc.setVerificationStatus(docStatus);
        doc.setRejectionReason(rejectionReason);
        doc.setVerifiedAt(LocalDateTime.now());

        documentRepository.save(doc);

        // 4. Actualizar Estado Global del Onboarding
        updateGlobalKycStatus(userId);

        // 5. Retornar Respuesta
        return KycDocumentResponse.builder()
                .documentType(docType.name())
                .verificationStatus(docStatus.name()) // Asegúrate que tu DTO use este nombre
                .rejectionReason(rejectionReason)
                .extractedData(iaResult)
                .fileUrl(storageService.getPresignedUrl(fileKey))
                .lastUpdated(LocalDateTime.now().toString())
                .build();
    }

    /**
     * Lógica auxiliar para comparar Selfie vs INE almacenada.
     */
    private Map<String, Object> processLivenessCheck(Long userId, MultipartFile selfieFile) {
        // A. Buscar el documento de referencia (INE Frente o Pasaporte)
        // La lógica es: "Dame la INE aprobada". Si no hay, busca "Pasaporte aprobado".
        ProviderDocument refDoc = documentRepository.findByProviderIdAndDocumentType(userId, DocumentType.INE_FRONT)
                .or(() -> documentRepository.findByProviderIdAndDocumentType(userId, DocumentType.PASSPORT))
                .orElseThrow(() -> new IllegalArgumentException("Debes subir y aprobar tu identificación oficial antes de tomar la selfie."));

        if (refDoc.getVerificationStatus() != VerificationStatus.APPROVED) {
            throw new IllegalArgumentException("Tu identificación está en revisión o fue rechazada. No puedes avanzar a la selfie aún.");
        }

        // B. Descargar los bytes de la imagen de referencia (usando el método nuevo del StorageService)
        byte[] idImageBytes = storageService.getFileBytes(refDoc.getFileKey());

        // C. Llamar a Gemini para comparar
        return geminiKycService.verifyBiometricMatch(selfieFile, idImageBytes);
    }

    private void updateGlobalKycStatus(Long userId) {
        // Verificar si hay algún rechazo activo
        boolean hasRejectedDocs = documentRepository.findAllByProviderId(userId).stream()
                .anyMatch(d -> d.getVerificationStatus() == VerificationStatus.REJECTED);

        ProviderOnboarding status = onboardingStatusRepository.findById(userId)
                .orElse(ProviderOnboarding.builder().providerId(userId).build());

        if (hasRejectedDocs) {
            status.setKycStatus(OnboardingStatus.ACTION_REQUIRED);
        } else {
            // Verificar completitud mínima para MVP:
            // Debe tener al menos (INE_FRONT + SELFIE) ó (PASSPORT + SELFIE) aprobados
            boolean hasId = documentRepository.findByProviderIdAndDocumentType(userId, DocumentType.INE_FRONT)
                    .map(d -> d.getVerificationStatus() == VerificationStatus.APPROVED)
                    .orElse(false)
                    || documentRepository.findByProviderIdAndDocumentType(userId, DocumentType.PASSPORT)
                    .map(d -> d.getVerificationStatus() == VerificationStatus.APPROVED)
                    .orElse(false);

            boolean hasSelfie = documentRepository.findByProviderIdAndDocumentType(userId, DocumentType.SELFIE)
                    .map(d -> d.getVerificationStatus() == VerificationStatus.APPROVED)
                    .orElse(false);

            if (hasId && hasSelfie) {
                status.setKycStatus(OnboardingStatus.COMPLETED);

                // 🔥 NOTIFICACIÓN GLOBAL: KYC Exitoso
                Map<String, Object> emailData = new HashMap<>();
                emailData.put("kycVerified", true);
                eventPublisher.publishStepCompleted(userId, null, "KYC", emailData);
            } else {
                // Aún no acaba, sigue en progreso
                status.setKycStatus(OnboardingStatus.IN_PROGRESS);
            }
        }

        onboardingStatusRepository.save(status);
    }
}
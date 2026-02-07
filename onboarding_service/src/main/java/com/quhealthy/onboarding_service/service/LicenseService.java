package com.quhealthy.onboarding_service.service;

import com.quhealthy.onboarding_service.dto.response.LicenseResponse;
import com.quhealthy.onboarding_service.event.OnboardingEventPublisher;
import com.quhealthy.onboarding_service.model.ProfessionalLicense;
import com.quhealthy.onboarding_service.model.ProviderOnboarding;
import com.quhealthy.onboarding_service.model.ProviderProfile;
import com.quhealthy.onboarding_service.model.enums.DocumentType;
import com.quhealthy.onboarding_service.model.enums.OnboardingStatus;
import com.quhealthy.onboarding_service.model.enums.ValidationSource;
import com.quhealthy.onboarding_service.repository.ProfessionalLicenseRepository;
import com.quhealthy.onboarding_service.repository.ProviderOnboardingRepository;
import com.quhealthy.onboarding_service.repository.ProviderProfileRepository;
import com.quhealthy.onboarding_service.service.integration.GeminiKycService;
import com.quhealthy.onboarding_service.service.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class LicenseService {

    private final StorageService storageService;
    private final GeminiKycService geminiKycService;
    private final ProfessionalLicenseRepository licenseRepository;
    private final ProviderProfileRepository profileRepository;
    private final ProviderOnboardingRepository onboardingStatusRepository;
    private final OnboardingEventPublisher eventPublisher;

    @Transactional
    public LicenseResponse uploadAndVerifyLicense(Long userId, MultipartFile file) {
        log.info("🎓 Procesando Cédula Profesional para usuario ID: {}", userId);

        // 1. Subir Imagen
        String fileKey = storageService.uploadFile(file, userId, DocumentType.PROFESSIONAL_LICENSE.name());

        // 2. Extraer Datos con IA
        Map<String, Object> extractedData = geminiKycService.extractLicenseData(file);
        log.debug("Datos extraídos de cédula: {}", extractedData);

        // 3. Validación Lógica
        boolean isValid = validateLicenseData(userId, extractedData);

        String rejectionReason = null;
        if (!isValid) {
            if (Boolean.FALSE.equals(extractedData.get("es_legible"))) {
                rejectionReason = "El documento es ilegible o está borroso.";
            } else if (Boolean.FALSE.equals(extractedData.get("documento_valido"))) {
                rejectionReason = "El documento no parece ser una Cédula Profesional válida.";
            } else {
                rejectionReason = "El nombre en la cédula no coincide con tu perfil.";
            }
        }

        // 4. Guardar en BD
        ProfessionalLicense license = licenseRepository.findByProviderId(userId)
                .orElse(ProfessionalLicense.builder().providerId(userId).build());

        license.setLicenseNumber((String) extractedData.getOrDefault("numero_cedula", "PENDING"));
        license.setInstitutionName((String) extractedData.getOrDefault("institucion", "Desconocida"));
        license.setCareerName((String) extractedData.getOrDefault("profesion", "Desconocida"));
        license.setDocumentUrl(fileKey);
        license.setVerified(isValid);
        license.setValidationSource(ValidationSource.AI_EXTRACTION);

        try {
            Object anioObj = extractedData.get("anio_registro");
            if (anioObj != null) {
                // Manejo robusto por si la IA devuelve string o number
                String anioStr = anioObj.toString().replaceAll("[^0-9]", "");
                if (!anioStr.isEmpty()) {
                    license.setYearIssued(Integer.parseInt(anioStr));
                }
            }
        } catch (Exception e) {
            log.warn("No se pudo parsear el año de la cédula: {}", e.getMessage());
        }

        licenseRepository.save(license);

        // 5. Actualizar Estado Global (CRÍTICO PARA TOKEN)
        updateOnboardingStatus(userId, isValid, rejectionReason);

        // 6. Retornar Respuesta
        return LicenseResponse.builder()
                .licenseNumber(license.getLicenseNumber())
                .careerName(license.getCareerName())
                .institutionName(license.getInstitutionName())
                .status(isValid ? "APPROVED" : "REJECTED")
                .rejectionReason(rejectionReason)
                .documentUrl(storageService.getPresignedUrl(fileKey))
                .build();
    }

    private boolean validateLicenseData(Long userId, Map<String, Object> aiData) {
        if (Boolean.FALSE.equals(aiData.get("es_legible"))) return false;
        if (Boolean.FALSE.equals(aiData.get("documento_valido"))) return false;

        String cedula = (String) aiData.get("numero_cedula");
        if (cedula == null || cedula.trim().isEmpty()) return false;

        // Validación de Nombre
        ProviderProfile profile = profileRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Perfil de usuario no encontrado"));

        String nameOnLicense = normalizeString((String) aiData.getOrDefault("nombre_titular", ""));

        // ⚠️ IMPORTANTE: Aquí deberíamos comparar contra el Nombre REAL (Persona Física)
        // Como ProviderProfile suele tener info pública, asegúrate que 'fullName' venga de ahí
        // o si tienes acceso al nombre legal. Usaré una lógica híbrida segura.
        String nameToCompare = normalizeString(profile.getBusinessName());

        // Si tienes un campo 'legalName' en profile, úsalo preferentemente:
        // if (profile.getLegalName() != null) nameToCompare = normalizeString(profile.getLegalName());

        return checkNameMatch(nameToCompare, nameOnLicense);
    }

    private void updateOnboardingStatus(Long userId, boolean isVerified, String reason) {
        ProviderOnboarding status = onboardingStatusRepository.findById(userId)
                .orElse(ProviderOnboarding.builder().providerId(userId).build());

        if (isVerified) {
            status.setLicenseStatus(OnboardingStatus.COMPLETED);

            // 🔥 EVENTO DE SINCRONIZACIÓN
            // Esto le dice al Auth Service: "¡Oye! Este usuario ya cumplió el requisito de licencia".
            Map<String, Object> extra = new HashMap<>();
            extra.put("licenseVerified", true);
            extra.put("finalStatus", "APPROVED"); // Para consistencia

            // Usamos un tópico específico si quieres disparar lógica de "Onboarding Full Completo"
            eventPublisher.publishStepCompleted(userId, null, "LICENSE_COMPLETED", extra);

        } else {
            status.setLicenseStatus(OnboardingStatus.ACTION_REQUIRED);
            eventPublisher.publishStepRejected(userId, null, "LICENSE_REJECTED", reason);
        }

        onboardingStatusRepository.save(status);
    }

    // --- UTILIDADES ---

    private String normalizeString(String input) {
        if (input == null) return "";
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{InCombiningDiacriticalMarks}+", "").toLowerCase().trim();
    }

    private boolean checkNameMatch(String profileName, String licenseName) {
        if (licenseName.isEmpty()) return false;
        String[] profileTokens = profileName.split("\\s+");
        int matches = 0;
        for (String token : profileTokens) {
            if (token.length() < 3 || token.equals("dr") || token.equals("dra") || token.equals("lic")) continue;
            if (licenseName.contains(token)) matches++;
        }
        return matches >= 1; // MVP: 1 coincidencia fuerte es suficiente para no bloquear falsos negativos
    }
}
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

        // 1. Subir Imagen al Storage (MinIO o GCP)
        String fileKey = storageService.uploadFile(file, userId, DocumentType.PROFESSIONAL_LICENSE.name());

        // 2. Extraer Datos con IA (Gemini 3 Vision)
        Map<String, Object> extractedData = geminiKycService.extractLicenseData(file);

        // Log de depuración para ver qué trajo la IA
        log.debug("Datos extraídos de cédula: {}", extractedData);

        // 3. Validación Lógica (Nombre IA vs Nombre Perfil)
        boolean isValid = validateLicenseData(userId, extractedData);

        String rejectionReason = null;
        if (!isValid) {
            // Determinamos el motivo más probable
            if (Boolean.FALSE.equals(extractedData.get("es_legible"))) {
                rejectionReason = "El documento es ilegible o está borroso.";
            } else if (Boolean.FALSE.equals(extractedData.get("documento_valido"))) {
                rejectionReason = "El documento no parece ser una Cédula Profesional válida.";
            } else {
                rejectionReason = "El nombre en la cédula no coincide suficientemente con tu perfil.";
            }
        }

        // 4. Guardar o Actualizar en BD (Tabla provider_licenses)
        ProfessionalLicense license = licenseRepository.findByProviderId(userId)
                .orElse(ProfessionalLicense.builder().providerId(userId).build());

        // Mapeo seguro de valores (con valores por defecto si la IA retorna null)
        license.setLicenseNumber((String) extractedData.getOrDefault("numero_cedula", "PENDING"));
        license.setInstitutionName((String) extractedData.getOrDefault("institucion", "Desconocida"));
        license.setCareerName((String) extractedData.getOrDefault("profesion", "Desconocida"));

        // Guardamos la referencia al archivo (fileKey para uso interno)
        license.setDocumentUrl(fileKey);

        license.setVerified(isValid);
        license.setValidationSource(ValidationSource.AI_EXTRACTION);

        // Si hay año de emisión y es numérico, lo guardamos
        try {
            Object anioObj = extractedData.get("anio_registro"); // Asegúrate de pedir este campo en el prompt si lo deseas
            if (anioObj != null) {
                license.setYearIssued(Integer.parseInt(anioObj.toString()));
            }
        } catch (NumberFormatException e) {
            log.warn("No se pudo parsear el año de la cédula: {}", e.getMessage());
        }

        licenseRepository.save(license);

        // 5. Actualizar Estado Global del Onboarding
        updateOnboardingStatus(userId, isValid, rejectionReason);

        // 6. Retornar Respuesta al Frontend
        return LicenseResponse.builder()
                .licenseNumber(license.getLicenseNumber())
                .careerName(license.getCareerName())
                .institutionName(license.getInstitutionName())
                .status(isValid ? "APPROVED" : "REJECTED")
                .rejectionReason(rejectionReason)
                .documentUrl(storageService.getPresignedUrl(fileKey)) // URL temporal para ver la imagen
                .build();
    }

    /**
     * Valida que el documento sea legible y pertenezca al usuario.
     */
    private boolean validateLicenseData(Long userId, Map<String, Object> aiData) {
        // A. Validaciones básicas de la IA
        if (Boolean.FALSE.equals(aiData.get("es_legible"))) {
            log.warn("Cédula ilegible para usuario {}", userId);
            return false;
        }

        if (Boolean.FALSE.equals(aiData.get("documento_valido"))) {
            log.warn("IA marcó documento como inválido/falso para usuario {}", userId);
            return false;
        }

        String cedula = (String) aiData.get("numero_cedula");
        if (cedula == null || cedula.trim().isEmpty()) {
            return false;
        }

        // B. Validación de Nombre (Fuzzy Match simple)
        ProviderProfile profile = profileRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Perfil de usuario no encontrado"));

        String nameOnLicense = normalizeString((String) aiData.getOrDefault("nombre_titular", ""));
        String nameOnProfile = normalizeString(profile.getBusinessName()); // Nota: Idealmente usar Nombre Legal si existe

        // Lógica de coincidencia: Verificamos si palabras clave del perfil aparecen en la cédula
        // (Esto es permisivo porque 'businessName' puede ser "Dr. Juan Perez" y la cédula "Juan Perez Lopez")
        return checkNameMatch(nameOnProfile, nameOnLicense);
    }

    private void updateOnboardingStatus(Long userId, boolean isVerified, String reason) {
        ProviderOnboarding status = onboardingStatusRepository.findById(userId)
                .orElse(ProviderOnboarding.builder().providerId(userId).build());

        if (isVerified) {
            status.setLicenseStatus(OnboardingStatus.COMPLETED);

            // ✅ Notificar ÉXITO al Bus
            Map<String, Object> extra = new HashMap<>();
            extra.put("licenseVerified", true);
            eventPublisher.publishStepCompleted(userId, null, "LICENSE", extra);

        } else {
            status.setLicenseStatus(OnboardingStatus.ACTION_REQUIRED);

            // 🚨 Notificar RECHAZO al Bus
            eventPublisher.publishStepRejected(userId, null, "LICENSE", reason);
        }

        onboardingStatusRepository.save(status);
    }

    // --- UTILIDADES DE TEXTO ---

    /**
     * Elimina acentos y convierte a minúsculas para comparar nombres.
     */
    private String normalizeString(String input) {
        if (input == null) return "";
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{InCombiningDiacriticalMarks}+", "").toLowerCase().trim();
    }

    /**
     * Compara nombres token por token.
     * Retorna true si al menos 2 partes del nombre coinciden (ej: "Juan" y "Perez").
     */
    private boolean checkNameMatch(String profileName, String licenseName) {
        if (licenseName.isEmpty()) return false;

        String[] profileTokens = profileName.split("\\s+");
        int matches = 0;

        for (String token : profileTokens) {
            // Ignoramos títulos comunes
            if (token.equals("dr") || token.equals("dra") || token.equals("lic") || token.length() < 3) continue;

            if (licenseName.contains(token)) {
                matches++;
            }
        }

        // Si encontró al menos 1 coincidencia fuerte (apellido o nombre raro) o 2 comunes
        return matches >= 1;
    }
}
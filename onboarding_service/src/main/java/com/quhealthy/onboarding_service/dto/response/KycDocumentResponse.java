package com.quhealthy.onboarding_service.dto.response;

import lombok.Builder;
import lombok.Data;
import java.util.Map;

@Data
@Builder
public class KycDocumentResponse {

    private String documentType;       // INE_FRONT, PASSPORT, SELFIE

    private String verificationStatus; // APPROVED, REJECTED, PENDING

    private String rejectionReason;    // Null si fue aprobado. Texto si falló.

    private String fileUrl;            // URL temporal para que el usuario vea su foto

    /**
     * Datos dinámicos devueltos por la IA.
     * * CASO 1 (Documento ID):
     * {
     * "nombre_completo": "Juan Perez",
     * "curp": "PERJ...",
     * "es_legible": true
     * }
     * * CASO 2 (Selfie/Biometría):
     * {
     * "is_same_person": true,
     * "liveness_check": "PASSED",
     * "confidence_score": 98
     * }
     */
    private Map<String, Object> extractedData;

    private String lastUpdated;        // Fecha ISO (LocalDateTime.toString())
}
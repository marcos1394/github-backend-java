package com.quhealthy.onboarding_service.model.enums;

public enum VerificationStatus {
    /**
     * El documento se subió pero aún no ha sido procesado por la IA.
     */
    PENDING,

    /**
     * Gemini está analizando el documento en este momento.
     */
    PROCESSING,

    /**
     * Documento válido. La IA extrajo datos y coinciden, o un humano lo aprobó.
     */
    APPROVED,

    /**
     * Documento inválido (borroso, alterado, no coincide, expirado).
     * Requiere resubida.
     */
    REJECTED,

    /**
     * La IA tuvo dudas (confidence bajo) y requiere que un admin lo revise.
     */
    MANUAL_REVIEW_NEEDED
}
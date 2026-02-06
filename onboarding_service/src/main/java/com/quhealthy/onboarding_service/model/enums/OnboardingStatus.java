package com.quhealthy.onboarding_service.model.enums;

public enum OnboardingStatus {

    /**
     * El usuario no ha iniciado este paso.
     */
    PENDING,

    /**
     * El usuario ha guardado datos parciales pero no ha finalizado.
     * (Ej: Subió la INE Frontal pero le falta el Reverso).
     */
    IN_PROGRESS,

    /**
     * Los datos fueron enviados y están siendo analizados
     * (por la IA o por un humano).
     */
    UNDER_REVIEW,

    /**
     * Se encontraron problemas (foto borrosa, datos no coinciden).
     * El usuario debe corregir y volver a enviar.
     */
    ACTION_REQUIRED,

    /**
     * Paso verificado y aprobado exitosamente.
     */
    COMPLETED,

    /**
     * Rechazo definitivo (Ej: Documento falso detectado, intento de fraude).
     * Bloquea el proceso y suspende la cuenta.
     */
    REJECTED,

    /**
     * Este paso no aplica para el plan o tipo de proveedor actual.
     */
    NOT_REQUIRED;

    /**
     * Helper para saber si el paso bloquea la operación.
     */
    public boolean isBlocking() {
        return this == PENDING || this == ACTION_REQUIRED || this == REJECTED;
    }

    /**
     * Helper para saber si ya se terminó (sea porque se completó o no aplica).
     */
    public boolean isFinished() {
        return this == COMPLETED || this == NOT_REQUIRED;
    }
}
package com.quhealthy.onboarding_service.model.enums;

public enum DocumentType {
    /**
     * Frente de la Credencial para Votar (INE/IFE).
     * Requerido si el usuario elige INE.
     */
    INE_FRONT,

    /**
     * Reverso de la Credencial para Votar (INE/IFE).
     * Requerido si el usuario elige INE.
     */
    INE_BACK,

    /**
     * Página de datos del Pasaporte Mexicano.
     * Alternativa a la INE. No requiere reverso.
     */
    PASSPORT,

    /**
     * Foto tomada en vivo para validación biométrica (Prueba de Vida).
     * Se compara contra la foto del INE/Pasaporte.
     */
    SELFIE,

    /**
     * Cédula Profesional (Estatal o Federal).
     * Validación de profesión (Paso siguiente).
     */
    PROFESSIONAL_LICENSE,

    /**
     * Constancia de Situación Fiscal (SAT).
     * Para facturación.
     */
    TAX_CERTIFICATE,

    /**
     * Comprobante de Domicilio (Luz, Agua, Internet).
     * Solo si la dirección del INE no coincide o es antigua.
     */
    PROOF_OF_ADDRESS
}
package com.quhealthy.onboarding_service.model.enums;

public enum ValidationSource {
    /**
     * Validado contra una base de datos oficial (ej: API Cédulas SEP, SAT).
     * Nivel de confianza: Máximo.
     */
    GOVERNMENT_API,

    /**
     * Extraído y validado por Inteligencia Artificial (Gemini 3).
     * Nivel de confianza: Alto (pero falible).
     */
    AI_EXTRACTION,

    /**
     * Revisado visualmente por un administrador del Backoffice.
     * Nivel de confianza: Alto (humano).
     */
    MANUAL_REVIEW,

    /**
     * El usuario declaró la información pero no se ha validado (Autodeclarativo).
     * Nivel de confianza: Bajo.
     */
    SELF_DECLARED
}
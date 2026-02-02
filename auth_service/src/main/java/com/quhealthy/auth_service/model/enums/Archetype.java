package com.quhealthy.auth_service.model.enums;

/**
 * Arquetipo del provider - Tipo de perfil profesional.
 *
 * Determina qué tipo de servicios puede ofrecer y cómo aparece en la plataforma.
 */
public enum Archetype {

    /**
     * Profesional de salud: doctor, dentista, psicólogo, etc.
     */
    HEALTHCARE_PROFESSIONAL(
            "Profesional de Salud",
            "Médico, dentista, psicólogo, nutricionista, etc.",
            true
    ),

    /**
     * Especialista en belleza: esteticien, peluquero, etc.
     */
    BEAUTY_SPECIALIST(
            "Especialista en Belleza",
            "Esteticien, peluquero, manicurista, etc.",
            true
    ),

    /**
     * Entrenador personal: fitness, yoga, crossfit, etc.
     */
    FITNESS_TRAINER(
            "Entrenador Personal",
            "Entrenador de fitness, yoga, pilates, etc.",
            true
    ),

    /**
     * Centro de bienestar: spa, wellness, etc.
     */
    WELLNESS_CENTER(
            "Centro de Bienestar",
            "SPA, centro de masajes, centro wellness, etc.",
            true
    ),

    /**
     * Clínica: clínica dental, clínica dermatológica, etc.
     */
    CLINIC(
            "Clínica",
            "Clínica dental, clínica dermatológica, clínica de cirugía, etc.",
            true
    ),

    /**
     * Consultorio: consultorio médico, psicológico, etc.
     */
    OFFICE(
            "Consultorio",
            "Consultorio médico, consultorio psicológico, consultorio legal, etc.",
            true
    ),

    /**
     * Institución educativa: academia, instituto, etc.
     */
    EDUCATIONAL_INSTITUTION(
            "Institución Educativa",
            "Academia, instituto, escuela de entrenamiento, etc.",
            true
    ),

    /**
     * Plataforma de servicios: agregador, marketplace, etc.
     */
    PLATFORM(
            "Plataforma de Servicios",
            "Agregador de profesionales, marketplace de servicios, etc.",
            true
    );

    private final String displayName;
    private final String description;
    private final boolean isActive;

    Archetype(String displayName, String description, boolean isActive) {
        this.displayName = displayName;
        this.description = description;
        this.isActive = isActive;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public boolean isActive() {
        return isActive;
    }
}
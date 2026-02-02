package com.quhealthy.auth_service.model.enums;

/**
 * Tipo de entidad legal del provider.
 *
 * PERSONA_FISICA: Profesional independiente (requiere genero)
 * EMPRESA: Clínica, centro, spa, etc. (no requiere genero)
 *
 * Se usa para determinar:
 * - Qué campos son requeridos en registro/onboarding
 * - Si el provider puede tener un "genero" (PERSONA_FISICA) o no (EMPRESA)
 * - Documentos requeridos (cédula vs RUT/RFC)
 * - Cómo aparece el nombre en la plataforma
 */
public enum LegalEntityType {

    /**
     * Profesional independiente.
     * Requiere: Género, Nombre y Apellido personal
     * Ejemplo: "Dr. Carlos Rodríguez" cardiólogo independiente
     */
    PERSONA_FISICA(
            "Persona Física",
            "Profesional independiente",
            true  // requiere genero
    ),

    /**
     * Entidad empresarial: clínica, centro, spa, etc.
     * No requiere: Género
     * Requiere: Nombre de empresa/negocio
     * Ejemplo: "Clínica del Corazón S.A." o "SPA Relax & Co."
     */
    EMPRESA(
            "Empresa",
            "Clínica, centro, spa, institución, etc.",
            false  // NO requiere genero
    );

    private final String displayName;
    private final String description;
    private final boolean requiresGender;

    LegalEntityType(String displayName, String description, boolean requiresGender) {
        this.displayName = displayName;
        this.description = description;
        this.requiresGender = requiresGender;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Indica si este tipo de entidad requiere un género.
     * PERSONA_FISICA = true
     * EMPRESA = false
     */
    public boolean requiresGender() {
        return requiresGender;
    }
}
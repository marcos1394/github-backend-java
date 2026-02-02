package com.quhealthy.auth_service.model.enums;

/**
 * Género del usuario.
 *
 * Solo aplica a CONSUMER y PROVIDER persona física.
 * Los PROVIDER empresa (EMPRESA) no tienen género.
 *
 * Usado para:
 * - Analytics
 * - Personalización de recomendaciones
 * - Búsquedas específicas (ej: "buscar ginecoólogo")
 */
public enum Gender {

    MALE("Masculino", "M"),
    FEMALE("Femenino", "F"),
    OTHER("Otro", "O"),
    PREFER_NOT_TO_SAY("Prefiero no decir", "P");

    private final String displayName;
    private final String code;

    Gender(String displayName, String code) {
        this.displayName = displayName;
        this.code = code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getCode() {
        return code;
    }

    public static Gender fromCode(String code) {
        for (Gender gender : Gender.values()) {
            if (gender.code.equalsIgnoreCase(code)) {
                return gender;
            }
        }
        return PREFER_NOT_TO_SAY;
    }
}
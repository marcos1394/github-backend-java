package com.quhealthy.auth_service.model.enums;

/**
 * Roles de usuario en el sistema.
 *
 * CONSUMER: Paciente/Consumidor de servicios
 * PROVIDER: Profesional de salud o belleza
 * ADMIN: Administrador del sistema (future)
 */
public enum Role {

    CONSUMER("Paciente", "Consume servicios de salud y belleza"),
    PROVIDER("Profesional", "Ofrece servicios de salud y belleza"),
    ADMIN("Administrador", "Gestiona la plataforma");

    private final String displayName;
    private final String description;

    Role(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}
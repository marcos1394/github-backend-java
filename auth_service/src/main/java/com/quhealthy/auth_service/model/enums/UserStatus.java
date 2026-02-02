package com.quhealthy.auth_service.model.enums;

/**
 * Estados posibles de un usuario en el sistema.
 *
 * ACTIVO: Usuario activo, puede hacer login
 * INACTIVO: No ha verificado el email aún
 * SUSPENDIDO: Ha violado términos de servicio
 * DEACTIVADO: El usuario deactivó su cuenta
 * ELIMINADO: Soft delete (no aparece en búsquedas pero data persiste)
 */
public enum UserStatus {

    ACTIVE("Activo", "Usuario activo y verificado"),
    INACTIVE("Inactivo", "Email no verificado"),
    SUSPENDED("Suspendido", "Suspendido por violación de términos"),
    DEACTIVATED("Desactivado", "Deactivado por el usuario"),
    DELETED("Eliminado", "Soft delete");

    private final String displayName;
    private final String description;

    UserStatus(String displayName, String description) {
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
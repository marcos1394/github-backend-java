package com.quhealthy.catalog_service.model.enums;

public enum ItemStatus {
    ACTIVE,    // Visible para los pacientes
    INACTIVE,  // Oculto temporalmente (ej: El doctor está de vacaciones)
    ARCHIVED   // "Eliminado" lógicamente (para mantener historial de órdenes viejas)
}
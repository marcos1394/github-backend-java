package com.quhealthy.catalog_service.model.enums;

public enum ServiceModality {
    IN_PERSON,  // En consultorio
    ONLINE,     // Videollamada (Telemedicina)
    HOME_VISIT, // A domicilio (Muy común en enfermería/geriatría)
    HYBRID,     // Parte online, parte físico
    NOT_APPLICABLE // Para productos
}
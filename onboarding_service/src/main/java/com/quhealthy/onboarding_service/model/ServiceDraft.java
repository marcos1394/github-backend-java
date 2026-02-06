package com.quhealthy.onboarding_service.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * POJO auxiliar para mapear el JSON de servicios iniciales.
 * No es una entidad JPA, vive dentro de la columna 'initial_services' (JSONB).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceDraft implements Serializable {

    private String name;           // Ej: "Consulta General"

    private BigDecimal price;      // Ej: 500.00

    private Integer durationMinutes; // Ej: 30

    private String description;    // Ej: "Incluye revisión básica..."
}
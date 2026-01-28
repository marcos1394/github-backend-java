package com.quhealthy.appointment_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 🌉 DTO PUENTE
 * Este objeto mapea la respuesta que viene del microservicio de Catálogo.
 * No es una entidad de BD, es solo para transporte de datos entre servicios.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CatalogServiceDto {
    private Long id;
    private String name;
    private String description;
    private BigDecimal price;
    private String currency; // "MXN"
    private Integer durationMinutes; // Vital para calcular el endTime
}
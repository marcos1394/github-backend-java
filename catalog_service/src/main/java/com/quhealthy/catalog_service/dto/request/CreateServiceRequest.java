package com.quhealthy.catalog_service.dto.request;

import com.quhealthy.catalog_service.model.enums.Currency;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateServiceRequest {

    @NotBlank(message = "El nombre del servicio es obligatorio")
    @Size(min = 3, max = 150, message = "El nombre debe tener entre 3 y 150 caracteres")
    private String name;

    @Size(max = 1000, message = "La descripción no puede exceder los 1000 caracteres")
    private String description;

    @NotNull(message = "El precio es obligatorio")
    @DecimalMin(value = "0.0", inclusive = false, message = "El precio debe ser mayor a 0")
    private BigDecimal price;

    private Currency currency = Currency.MXN; // Opcional, default en backend

    @NotNull(message = "La duración es obligatoria")
    @Min(value = 5, message = "La duración mínima es de 5 minutos")
    private Integer durationMinutes;
}
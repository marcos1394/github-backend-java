package com.quhealthy.catalog_service.dto.request;

import com.quhealthy.catalog_service.model.enums.Currency;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class CreatePackageRequest {

    @NotBlank(message = "El nombre del paquete es obligatorio")
    @Size(max = 150)
    private String name;

    private String description;

    @NotNull(message = "El precio del paquete es obligatorio")
    @DecimalMin(value = "0.0", inclusive = false)
    private BigDecimal price; // Precio final ofertado

    private Currency currency = Currency.MXN;

    @NotEmpty(message = "El paquete debe incluir al menos un servicio")
    private List<Long> serviceIds; // IDs de los servicios existentes que conforman el paquete
}
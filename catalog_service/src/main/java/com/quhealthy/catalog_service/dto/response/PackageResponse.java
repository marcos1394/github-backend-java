package com.quhealthy.catalog_service.dto.response;

import com.quhealthy.catalog_service.model.enums.Currency;
import com.quhealthy.catalog_service.model.enums.ServiceStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class PackageResponse {
    private Long id;
    private String name;
    private String description;
    private BigDecimal price;
    private Currency currency;
    private ServiceStatus status;
    
    // Lista anidada de los servicios que incluye
    private List<ServiceResponse> services;
    
    // (Opcional) Calculado: Cuánto costaría comprar los servicios por separado
    private BigDecimal realValue; 
    // (Opcional) Calculado: Cuánto se ahorra el paciente
    private BigDecimal savings;
}
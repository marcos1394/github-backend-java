package com.quhealthy.catalog_service.dto.response;

import com.quhealthy.catalog_service.model.enums.Currency;
import com.quhealthy.catalog_service.model.enums.ServiceStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class ServiceResponse {
    private Long id;
    private String name;
    private String description;
    private BigDecimal price;
    private Currency currency;
    private Integer durationMinutes;
    private ServiceStatus status;
}
package com.quhealthy.catalog_service.dto;

import com.quhealthy.catalog_service.model.enums.ItemType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CatalogItemSummary {
    private Long id;
    private String name;
    private ItemType type;
    private String imageUrl;
    private BigDecimal price; // Valor original del ítem dentro del paquete
    private String category;
}
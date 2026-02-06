package com.quhealthy.catalog_service.dto;

import com.quhealthy.catalog_service.model.enums.Currency;
import com.quhealthy.catalog_service.model.enums.ItemStatus;
import com.quhealthy.catalog_service.model.enums.ItemType;
import com.quhealthy.catalog_service.model.enums.ServiceModality;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CatalogItemResponse {

    private Long id;
    private Long providerId;
    private ItemType type;
    private String name;
    private String description;
    private String imageUrl;
    private String category;

    // --- Precios ---
    private BigDecimal price;
    private BigDecimal compareAtPrice;
    private Currency currency;

    // Campo Calculado: Porcentaje de ahorro (Ej: 20)
    private Integer discountPercentage;

    // --- Ubicación ---
    private Double latitude;
    private Double longitude;
    private String locationName;
    // Distancia calculada respecto al usuario (se llena en búsqueda geo)
    private Double distanceKm;

    // --- Datos Específicos ---
    private Integer durationMinutes;
    private ServiceModality modality;
    private String sku;
    private Integer stockQuantity;
    private Boolean isDigital;

    // --- Contenido del Paquete ---
    // Si es un paquete, devolvemos un resumen de lo que incluye.
    private Set<CatalogItemSummary> packageContents;

    // --- Metadata y Social Proof ---
    private Map<String, Object> metadata;
    private Set<String> searchTags;
    private Double averageRating;
    private Integer reviewCount;

    private ItemStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
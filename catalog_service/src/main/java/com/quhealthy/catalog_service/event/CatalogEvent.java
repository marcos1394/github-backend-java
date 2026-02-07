package com.quhealthy.catalog_service.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CatalogEvent implements Serializable {

    private String eventId;

    // Tipos: ITEM_CREATED, ITEM_UPDATED, ITEM_ARCHIVED, STORE_UPDATED
    private String eventType;

    private Long providerId;

    // Datos flexibles (ID del producto, nombre, precio anterior, etc.)
    private Map<String, Object> payload;

    private LocalDateTime timestamp;
}
package com.quhealthy.catalog_service.dto;

import com.quhealthy.catalog_service.model.enums.Currency;
import com.quhealthy.catalog_service.model.enums.ItemStatus;
import com.quhealthy.catalog_service.model.enums.ItemType;
import com.quhealthy.catalog_service.model.enums.ServiceModality;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CatalogItemRequest {

    // --- Identificación ---

    @NotNull(message = "El tipo de ítem es obligatorio (SERVICE, PRODUCT, PACKAGE)")
    private ItemType type;

    @NotBlank(message = "El nombre es obligatorio")
    @Size(min = 3, max = 150, message = "El nombre debe tener entre 3 y 150 caracteres")
    private String name;

    @NotBlank(message = "La categoría es obligatoria")
    private String category; // Ej: "SALUD", "BELLEZA"

    private String description;
    private String imageUrl;

    // --- Precios & Marketing ---

    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    private BigDecimal price;

    private BigDecimal compareAtPrice; // Precio anterior (para ofertas)

    @Builder.Default
    private Currency currency = Currency.MXN;

    private BigDecimal taxRate; // Ej: 0.16

    // --- Geolocalización (Innovación) ---
    // El frontend envía esto si el servicio es físico.

    private Double latitude;
    private Double longitude;
    private String locationName;

    // --- Específico de Servicios ---

    @Min(value = 5, message = "La duración mínima es de 5 minutos")
    private Integer durationMinutes;

    private ServiceModality modality; // ONLINE, IN_PERSON

    // --- Específico de Productos ---

    private String sku;
    private Integer stockQuantity;
    private Boolean isDigital;

    // --- Específico de Paquetes ---
    // El doctor selecciona IDs de servicios existentes para armar el paquete.
    private Set<Long> packageItemIds;

    // --- Extras ---

    private Set<String> searchTags; // Para la búsqueda AI
    private Map<String, Object> metadata; // Atributos flexibles (JSONB)

    private ItemStatus status; // ACTIVE, INACTIVE
}
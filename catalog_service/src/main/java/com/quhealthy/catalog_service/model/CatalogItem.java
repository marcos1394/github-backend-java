package com.quhealthy.catalog_service.model;

import com.quhealthy.catalog_service.model.enums.Currency;
import com.quhealthy.catalog_service.model.enums.ItemType;
import com.quhealthy.catalog_service.model.enums.ItemStatus;
import com.quhealthy.catalog_service.model.enums.ServiceModality;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "catalog_items", indexes = {
        @Index(name = "idx_catalog_provider", columnList = "provider_id"),
        @Index(name = "idx_catalog_type", columnList = "type"),
        @Index(name = "idx_catalog_category", columnList = "category")
})
@EntityListeners(AuditingEntityListener.class)
public class CatalogItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "provider_id", nullable = false)
    private Long providerId; // El Doctor dueño

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ItemType type; // SERVICE, PRODUCT, PACKAGE

    // --- Datos Comunes ---

    @NotBlank
    @Column(nullable = false, length = 150)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "image_url")
    private String imageUrl;

    @NotBlank
    @Column(length = 50)
    private String category; // "SALUD", "BELLEZA", "SUPLEMENTOS"

    // --- Precios ---

    @NotNull
    @DecimalMin(value = "0.0")
    @Column(precision = 10, scale = 2, nullable = false)
    private BigDecimal price;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(length = 3)
    private Currency currency = Currency.MXN;

    @Column(name = "tax_rate", precision = 5, scale = 2)
    private BigDecimal taxRate; // 0.16 para IVA

    // --- Lógica de Servicios ---

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Enumerated(EnumType.STRING)
    private ServiceModality modality;

    // --- 📍 GEOLOCALIZACIÓN (Innovación 1) ---
    // Denormalizamos esto desde el Onboarding.
    // Permite que un doctor ofrezca servicios en distintas sucursales.
    // Si es ONLINE, estos pueden ser nulos o la ubicación del consultorio base.

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "location_name", length = 100)
    private String locationName; // Ej: "Sucursal Roma Norte"

    // --- 🏷️ MARKETING & SMART PRICING (Innovación 2) ---

    // Precio "Antes" (para mostrar descuentos: "Antes $1000, Ahora $800")
    @Column(name = "compare_at_price", precision = 10, scale = 2)
    private BigDecimal compareAtPrice;

    // Etiquetas para búsqueda inteligente (Postgres Arrays)
    // Ej: ["dolor", "espalda", "ajuste", "huesos"]
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "search_tags", columnDefinition = "text[]")
    private Set<String> searchTags = new HashSet<>();

    // --- ⭐ SOCIAL PROOF (Innovación 3) ---
    // Se actualiza asíncronamente cuando alguien deja una review.
    // Vital para ordenar resultados por "Mejor Calificados".

    @Column(name = "average_rating")
    private Double averageRating; // 0.0 a 5.0

    @Column(name = "review_count")
    private Integer reviewCount; // Total de reseñas

    // --- Lógica de Productos ---

    @Column(name = "sku", length = 50)
    private String sku;

    @Column(name = "stock_quantity")
    private Integer stockQuantity;

    @Column(name = "is_digital")
    private Boolean isDigital;

    // --- LÓGICA DE PAQUETES (Self-Join Enterprise) ---
    // Esta relación permite que un ITEM (tipo PACKAGE) contenga otros ITEMS (SERVICE o PRODUCT)
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "package_contents", // Tabla intermedia automática
            joinColumns = @JoinColumn(name = "package_id"),
            inverseJoinColumns = @JoinColumn(name = "item_id")
    )
    @ToString.Exclude // Evita loops infinitos en logs
    @EqualsAndHashCode.Exclude
    private Set<CatalogItem> packageItems = new HashSet<>();

    // --- Metadata Flexible ---

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    // --- Estado ---

    @NotNull
    @Builder.Default
    @Enumerated(EnumType.STRING)
    private ItemStatus status = ItemStatus.ACTIVE;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
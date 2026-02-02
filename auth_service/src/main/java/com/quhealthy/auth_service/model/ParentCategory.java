package com.quhealthy.auth_service.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Entidad ParentCategory - Categorías padre del sistema.
 *
 * Ejemplos: "Salud", "Belleza"
 *
 * Esta es la categorización de primer nivel. Los providers eligen una
 * categoría padre al registrarse.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "parent_categories", indexes = {
        @Index(name = "idx_parent_categories_slug", columnList = "slug", unique = true),
        @Index(name = "idx_parent_categories_is_active", columnList = "is_active"),
        @Index(name = "idx_parent_categories_display_order", columnList = "display_order")
})
public class ParentCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Nombre único de la categoría padre.
     * Ejemplos: "Salud", "Belleza"
     */
    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;

    /**
     * Slug para URLs amigables e inmutable.
     * Ejemplos: "salud", "belleza"
     *
     * Se usa en URLs y APIs. NUNCA debe cambiar para no romper links.
     */
    @Column(name = "slug", nullable = false, unique = true, length = 100)
    private String slug;

    /**
     * Descripción de la categoría padre.
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * URL del ícono para mostrar en UI.
     */
    @Column(name = "icon_url", columnDefinition = "TEXT")
    private String iconUrl;

    /**
     * Orden de visualización en frontend.
     * Permite controlar el orden sin depender de ID.
     */
    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    /**
     * Indica si esta categoría está activa.
     *
     * Si is_active = false:
     * - No aparece en dropdown de registro
     * - Providers existentes la mantienen
     */
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    /**
     * Caché del número de providers en esta categoría.
     * Se actualiza mediante eventos o jobs.
     * Útil para mostrar "N profesionales disponibles".
     */
    @Column(name = "provider_count")
    private Integer providerCount = 0;

    // ========================================================================
    // RELACIONES
    // ========================================================================

    /**
     * Categorías hijas (CategoryProvider).
     * Ej: ParentCategory "Salud" tiene CategoryProvider "Cardiólogo", "Ginecólogo", etc.
     */
    @OneToMany(mappedBy = "parentCategory", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<CategoryProvider> categories;

    // ========================================================================
    // AUDITORÍA
    // ========================================================================

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
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
 * Entidad CategoryProvider - Categorías intermedias del sistema.
 *
 * Ejemplos: "Cardiólogo", "Ginecólogo", "Dermatólogo" (bajo Salud)
 *           "SPA", "Entrenador Personal" (bajo Belleza)
 *
 * Esta es la categorización de segundo nivel.
 * Un provider elige una CategoryProvider como su especialidad.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "category_providers", indexes = {
        @Index(name = "idx_category_providers_slug", columnList = "slug", unique = true),
        @Index(name = "idx_category_providers_parent_category_id", columnList = "parent_category_id"),
        @Index(name = "idx_category_providers_is_active", columnList = "is_active"),
        @Index(name = "idx_category_providers_display_order", columnList = "display_order")
})
public class CategoryProvider {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Nombre único de la categoría.
     * Ejemplos: "Cardiólogo", "Ginecólogo", "Dermatólogo"
     */
    @Column(name = "name", nullable = false, unique = true, length = 150)
    private String name;

    /**
     * Slug para URLs amigables e inmutable.
     * Ejemplos: "cardiologo", "ginecologo", "dermatologo"
     *
     * Se usa en URLs y APIs. NUNCA debe cambiar para no romper links.
     */
    @Column(name = "slug", nullable = false, unique = true, length = 150)
    private String slug;

    /**
     * Descripción de la categoría.
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * URL del ícono para mostrar en UI.
     */
    @Column(name = "icon_url", columnDefinition = "TEXT")
    private String iconUrl;

    /**
     * Orden de visualización dentro de su categoría padre.
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
     * Categoría padre.
     * Ejemplo: CategoryProvider "Cardiólogo" pertenece a ParentCategory "Salud"
     *
     * EAGER loading porque casi siempre necesitamos saber el padre.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "parent_category_id", nullable = false)
    private ParentCategory parentCategory;

    /**
     * Subcategorías (opcional, tercer nivel de clasificación).
     * Ejemplo: CategoryProvider "Cardiólogo" puede tener SubCategory "Pediátrico", "Adulto", etc.
     */
    @OneToMany(mappedBy = "category", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<SubCategory> subcategories;

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
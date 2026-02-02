package com.quhealthy.auth_service.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Entidad SubCategory - Categorías de tercer nivel (opcional).
 *
 * Ejemplos bajo CategoryProvider "Cardiólogo":
 * - "Cardiólogo Pediátrico"
 * - "Cardiólogo de Adultos"
 * - "Cardiólogo Interventor"
 *
 * Esta es la categorización de tercer nivel y es OPCIONAL.
 * Un provider puede no tener subcategoría si la categoría padre es genérica.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = "category")
@Entity
@Table(name = "sub_categories", indexes = {
        @Index(name = "idx_sub_categories_slug", columnList = "slug", unique = true),
        @Index(name = "idx_sub_categories_category_id", columnList = "category_id"),
        @Index(name = "idx_sub_categories_is_active", columnList = "is_active")
})
public class SubCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Nombre de la subcategoría.
     * Ejemplos: "Pediátrico", "Adultos", "Geriátrico"
     */
    @Column(name = "name", nullable = false, length = 150)
    private String name;

    /**
     * Slug para URLs amigables e inmutable.
     * Ejemplos: "pediatrico", "adultos", "geriatrico"
     */
    @Column(name = "slug", nullable = false, unique = true, length = 150)
    private String slug;

    /**
     * Descripción de la subcategoría.
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * URL del ícono para mostrar en UI.
     */
    @Column(name = "icon_url", columnDefinition = "TEXT")
    private String iconUrl;

    /**
     * Orden de visualización.
     * Permite controlar el orden sin depender de ID.
     */
    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    /**
     * Indica si esta subcategoría está activa.
     *
     * Si is_active = false:
     * - No aparece en dropdown de registro
     * - Providers existentes la mantienen
     */
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    /**
     * Caché del número de providers en esta subcategoría.
     * Se actualiza mediante eventos o jobs.
     */
    @Column(name = "provider_count")
    private Integer providerCount = 0;

    // ========================================================================
    // RELACIONES
    // ========================================================================

    /**
     * Categoría padre.
     * SubCategory "Pediátrico" pertenece a CategoryProvider "Cardiólogo"
     * que a su vez pertenece a ParentCategory "Salud"
     *
     * LAZY loading porque a veces solo necesitamos subcategoría sin padre.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private CategoryProvider category;

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
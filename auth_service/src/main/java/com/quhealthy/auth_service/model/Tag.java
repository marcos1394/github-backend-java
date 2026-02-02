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
import java.util.HashSet;
import java.util.Set;

/**
 * Entidad Tag - Etiquetas para caracterizar providers.
 *
 * Ejemplos: "Certificado", "Bilingüe", "Acreditado", "Top Rated", "Nuevo"
 *
 * Un provider puede tener múltiples tags para mejorar su visibilidad
 * y permitir búsquedas más específicas.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "tags", indexes = {
        @Index(name = "idx_tags_slug", columnList = "slug", unique = true),
        @Index(name = "idx_tags_is_active", columnList = "is_active")
})
public class Tag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Nombre único del tag.
     * Ejemplos: "Certificado", "Bilingüe", "Acreditado"
     */
    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;

    /**
     * Slug para URLs amigables e inmutable.
     * Ejemplos: "certificado", "bilingue", "acreditado"
     */
    @Column(name = "slug", nullable = false, unique = true, length = 100)
    private String slug;

    /**
     * Descripción del tag.
     * Explica qué significa y cómo se obtiene.
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * Color hexadecimal para mostrar el tag en UI.
     * Ejemplos: "#FF5733", "#33FF57", "#3357FF"
     */
    @Column(name = "color", length = 7)
    private String color;

    /**
     * Ícono del tag (URL o nombre).
     */
    @Column(name = "icon_url", columnDefinition = "TEXT")
    private String iconUrl;

    /**
     * Indica si este tag está activo.
     *
     * Si is_active = false:
     * - No se puede asignar a nuevos providers
     * - Providers existentes lo mantienen
     */
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    // ========================================================================
    // RELACIONES
    // ========================================================================

    /**
     * Providers que tienen este tag.
     *
     * Relación inversa de Provider.tags
     * Se usa para queries tipo: "encontrar todos los providers con tag 'Certificado'"
     */
    @ManyToMany(mappedBy = "tags", fetch = FetchType.LAZY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Set<Provider> providers = new HashSet<>();

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
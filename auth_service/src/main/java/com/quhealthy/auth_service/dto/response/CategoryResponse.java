package com.quhealthy.auth_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO para Categorías de Nivel 2 (CategoryProvider).
 * Ejemplos: "Cardiólogo", "Dentista", "Nutriólogo".
 *
 * USO:
 * Se utiliza dentro de ParentCategoryResponse o en listados de búsqueda.
 *
 * ESTRATEGIA DE CARGA:
 * Incluye la lista de 'subCategories' (Nivel 3) para permitir
 * que el Frontend construya selectores en cascada completos.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryResponse {

    /**
     * ID de la categoría (CategoryProvider).
     * Este es el ID que se envía en 'RegisterProviderRequest.parentCategoryId'
     * (aunque el nombre del campo en el request sugiere parent, la lógica
     * de negocio suele requerir este nivel específico para definir la especialidad).
     */
    private Long id;

    /**
     * Nombre legible.
     * Ej: "Cardiólogo"
     */
    private String name;

    /**
     * Slug para URLs.
     * Ej: "cardiologo"
     */
    private String slug;

    /**
     * Descripción corta para tooltips o subtítulos en la UI.
     */
    private String description;

    /**
     * URL del ícono (SVG/PNG) para mostrar en el Grid de categorías.
     */
    private String iconUrl;

    // ========================================================================
    // 🔽 JERARQUÍA (Hijos)
    // ========================================================================

    /**
     * Lista de Subcategorías (Nivel 3).
     * Ej: [Pediátrico, Intervencionista, Geriátrico]
     *
     * Si la categoría es genérica (ej: "Médico General"), esta lista
     * puede venir vacía.
     */
    private List<SubCategoryResponse> subCategories;
}
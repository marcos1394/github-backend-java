package com.quhealthy.auth_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO para Categorías de Nivel 1 (ParentCategory).
 * Ejemplos: "Salud", "Belleza", "Bienestar".
 *
 * USO:
 * - Menú principal de navegación.
 * - Primer paso del registro de proveedores (Selección de Industria).
 * - Filtros globales de búsqueda.
 *
 * ESTRUCTURA:
 * Contiene la lista de 'categories' (Nivel 2), formando un árbol anidado.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParentCategoryResponse {

    /**
     * ID de la Categoría Padre.
     */
    private Long id;

    /**
     * Nombre principal.
     * Ej: "Salud"
     */
    private String name;

    /**
     * Slug global.
     * Ej: "salud" -> quhealthy.com/salud
     */
    private String slug;

    /**
     * Descripción general de la vertical de negocio.
     */
    private String description;

    /**
     * URL del ícono o ilustración representativa.
     */
    private String iconUrl;

    /**
     * Color de tema (opcional).
     * Útil si cada sección de la app tiene un color distinto (Salud=Azul, Belleza=Rosa).
     */
    private String colorHex;

    // ========================================================================
    // 🔽 JERARQUÍA (Hijos)
    // ========================================================================

    /**
     * Lista de Categorías de Nivel 2 (CategoryProvider).
     *
     * Al incluir esto, el JSON resultante es:
     * {
     * "name": "Salud",
     * "categories": [
     * {
     * "name": "Cardiólogo",
     * "subCategories": [...]
     * }
     * ]
     * }
     */
    private List<CategoryResponse> categories;
}
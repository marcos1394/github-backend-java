package com.quhealthy.auth_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para Subcategorías de Nivel 3.
 * Ejemplos: "Pediátrico", "Adultos", "Geriátrico", "Invisalign".
 *
 * USO:
 * - Se utiliza dentro de CategoryResponse.
 * - Sirve para llenar el tercer dropdown en el registro.
 * - Se usa como filtro específico en la barra de búsqueda.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubCategoryResponse {

    /**
     * ID de la subcategoría.
     */
    private Long id;

    /**
     * Nombre legible.
     * Ej: "Pediátrico"
     */
    private String name;

    /**
     * Slug para URLs.
     * Ej: "pediatrico"
     * URL resultante: quhealthy.com/salud/cardiologo/pediatrico
     */
    private String slug;

    /**
     * Descripción breve.
     * Útil para mostrar tooltips de ayuda en el formulario de registro.
     */
    private String description;

    /**
     * URL del ícono (opcional).
     * Algunas interfaces visuales muestran íconos pequeños para subcategorías.
     */
    private String iconUrl;
}
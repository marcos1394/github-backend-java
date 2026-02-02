package com.quhealthy.auth_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para Etiquetas (Tags).
 * Ejemplos: "Certificado", "Bilingüe", "Acepta Seguros", "Wifi Gratis".
 *
 * USO:
 * - Mostrar los distintivos en el perfil del proveedor.
 * - Listar opciones en el filtro de búsqueda "Más características".
 * - Selección múltiple en el formulario de edición de perfil.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TagResponse {

    /**
     * ID del tag.
     * Este valor se envía en el set 'tagIds' al actualizar el perfil.
     */
    private Long id;

    /**
     * Nombre visible.
     * Ej: "Telemedicina Disponible"
     */
    private String name;

    /**
     * Slug único.
     * Ej: "telemedicina"
     */
    private String slug;

    /**
     * Descripción corta.
     * Explica qué significa tener este tag (útil para certificaciones médicas).
     */
    private String description;

    /**
     * Código de color HEX para la UI.
     * Permite que el backend controle la identidad visual de los tags.
     * Ej: "#34D399" (Verde), "#EF4444" (Rojo)
     */
    private String color;

    /**
     * URL del ícono (SVG/PNG).
     * Para mostrar un pequeño gráfico al lado del texto del tag.
     */
    private String iconUrl;
}
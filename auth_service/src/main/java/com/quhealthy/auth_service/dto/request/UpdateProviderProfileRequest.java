package com.quhealthy.auth_service.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * DTO para actualizar el perfil del Proveedor (Provider).
 *
 * USO:
 * Este request se utiliza en la sección "Mi Perfil" o "Ajustes" del Dashboard.
 * Permite al profesional mantener actualizada su información pública y de contacto.
 *
 * NO INCLUYE:
 * - Datos sensibles de facturación (Stripe).
 * - Documentos legales (Licencias/KYC).
 * - Cambio de email (requiere flujo separado de verificación).
 *
 * NOTA: Todos los campos son opcionales para permitir actualizaciones parciales (PATCH).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProviderProfileRequest {

    // ========================================================================
    // 🏢 IDENTIDAD DE NEGOCIO (Lo que ven los pacientes)
    // ========================================================================

    @Size(min = 3, max = 200, message = "El nombre del negocio debe tener entre 3 y 200 caracteres")
    private String businessName;

    /**
     * URL de la nueva imagen de perfil o logo.
     */
    private String profileImageUrl;

    /**
     * Biografía profesional.
     * Vital para el SEO y la conversión de pacientes.
     */
    @Size(max = 1000, message = "La biografía no debe exceder los 1000 caracteres")
    private String bio;

    // ========================================================================
    // 👤 IDENTIDAD PERSONAL (Solo si es Persona Física)
    // ========================================================================

    @Size(min = 2, max = 50, message = "El nombre debe tener entre 2 y 50 caracteres")
    private String firstName;

    @Size(min = 2, max = 50, message = "El apellido debe tener entre 2 y 50 caracteres")
    private String lastName;

    /**
     * Género del profesional.
     * Útil si el paciente busca "Ginecólogo (Mujer)".
     */
    @Pattern(regexp = "^(MALE|FEMALE|OTHER|PREFER_NOT_TO_SAY)$", message = "Valor de género inválido")
    private String gender;

    // ========================================================================
    // 📞 CONTACTO Y UBICACIÓN (Geolocalización)
    // ========================================================================

    @Size(min = 10, max = 15, message = "El teléfono debe tener entre 10 y 15 caracteres")
    private String phone;

    @Size(max = 400, message = "La dirección no debe exceder los 400 caracteres")
    private String address;

    /**
     * Latitud y Longitud.
     * El Frontend suele enviar esto cuando el usuario mueve el "pin" en el mapa
     * dentro de la configuración de su consultorio.
     * El Backend usará estos datos para actualizar el campo 'location' (PostGIS).
     */
    private Double latitude;
    private Double longitude;

    // ========================================================================
    // 🏷️ CATEGORIZACIÓN Y ETIQUETAS
    // ========================================================================

    /**
     * Permite cambiar la especialidad (Ej: de "Odontólogo General" a "Ortodoncista").
     * Nota: Cambiar esto podría requerir re-verificación de licencia en lógica de negocio,
     * pero el DTO debe permitir recibir el dato.
     */
    private Long categoryProviderId;

    private Long subCategoryId;

    /**
     * Lista de IDs de los Tags que el proveedor quiere tener activos.
     * Ej: [10 (Bilingüe), 25 (Telemedicina)]
     *
     * El servicio borrará las asociaciones previas y creará las nuevas según esta lista.
     */
    private Set<Long> tagIds;
}
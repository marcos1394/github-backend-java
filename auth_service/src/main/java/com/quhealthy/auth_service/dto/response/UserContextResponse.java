package com.quhealthy.auth_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO de Contexto de Usuario (Ligero).
 *
 * PROPÓSITO:
 * Servir como objeto de intercambio de identidad entre microservicios.
 *
 * ESCENARIOS DE USO:
 * 1. Validación de Token: El Gateway llama a Auth para validar un token y recibe esto.
 * 2. Comunicación Inter-servicio: El microservicio de 'Citas' pregunta a Auth:
 * "¿Quién es el usuario con ID 5?" y recibe este resumen.
 *
 * CARACTERÍSTICAS:
 * - NO contiene datos de UI (fotos, bio, colores).
 * - SÍ contiene datos de Ruteo y Seguridad (IDs, Roles, Permisos).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserContextResponse {

    /**
     * ID único del usuario (Primary Key en la BD de Auth).
     */
    private Long userId;

    /**
     * Email del usuario (Identificador único funcional).
     */
    private String email;

    /**
     * Rol principal: "PROVIDER", "CONSUMER", "ADMIN".
     * Vital para la autorización (RBAC) en otros servicios.
     */
    private String role;

    // ========================================================================
    // 👤 DATOS HUMANOS BÁSICOS
    // ========================================================================

    private String firstName;
    private String lastName;

    /**
     * Nombre completo concatenado para logs o auditoría en otros servicios.
     */
    private String fullName;

    // ========================================================================
    // 🏢 DATOS DE NEGOCIO (Solo para Providers)
    // ========================================================================

    /**
     * ID del Negocio/Clínica asociado.
     * CRÍTICO: Otros microservicios usarán este ID para filtrar datos.
     * Ej: El servicio de inventario necesita saber el 'businessId' para mostrar SU stock.
     * (Será null si es Consumer).
     */
    private Long businessId;

    /**
     * Nombre del negocio.
     * Útil para emails transaccionales enviados desde otros servicios.
     */
    private String businessName;

    // ========================================================================
    // 🛡️ SEGURIDAD Y ESTADO
    // ========================================================================

    /**
     * ¿Está verificada la cuenta?
     * Otros servicios podrían bloquear acciones sensibles si es false.
     */
    private boolean isVerified;

    /**
     * Lista de permisos granulares (si usas Authorities además de Roles).
     * Ej: ["READ_APPOINTMENTS", "WRITE_PRESCRIPTIONS"]
     */
    private List<String> authorities;
}
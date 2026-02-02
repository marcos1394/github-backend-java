package com.quhealthy.auth_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO de respuesta tras el registro exitoso de un Proveedor (Provider).
 *
 * FLUJO DE USO:
 * 1. Frontend envía POST /register/provider
 * 2. Backend crea la entidad Provider (Negocio + Persona) y envía email de verificación.
 * 3. Backend devuelve este objeto con HTTP 201 Created.
 * 4. Frontend usa estos datos para mostrar la pantalla de "Confirma tu email".
 *
 * NOTA DE SEGURIDAD:
 * No devolvemos el JWT aquí.
 * En HealthTech, es vital verificar el canal de comunicación (email) antes
 * de permitir cualquier acceso al Dashboard o a datos sensibles.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderRegistrationResponse {

    /**
     * ID único del proveedor creado.
     */
    private Long id;

    /**
     * El email registrado donde se envió el token.
     * Vital mostrarlo en la UI por si el usuario tuvo un "typo" (error de dedo)
     * y necesita saber por qué no le llega el correo.
     */
    private String email;

    /**
     * Nombre comercial registrado.
     * Ej: "Clínica Dental Sonrisas"
     */
    private String businessName;

    /**
     * Nombre del titular de la cuenta.
     * Ej: "Juan"
     */
    private String firstName;

    /**
     * Mensaje de instrucción para el usuario.
     * Ej: "Cuenta creada. Por favor verifica tu bandeja de entrada para activar tu perfil."
     */
    private String message;

    /**
     * Timestamp de creación.
     */
    private LocalDateTime createdAt;
}
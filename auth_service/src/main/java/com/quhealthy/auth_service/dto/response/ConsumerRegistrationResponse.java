package com.quhealthy.auth_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO de respuesta tras el registro exitoso de un Paciente.
 *
 * FLUJO DE USO:
 * 1. Frontend envía POST /register/consumer
 * 2. Backend crea el usuario y envía email de verificación.
 * 3. Backend devuelve este objeto.
 * 4. Frontend usa estos datos para mostrar:
 * "Hola [firstName], hemos enviado un enlace a [email]. Por favor verifícalo."
 *
 * NOTA:
 * No devolvemos JWT aquí por seguridad. El usuario debe verificar su email
 * antes de obtener su primer token de sesión.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsumerRegistrationResponse {

    /**
     * ID del usuario creado.
     * Útil para analíticas o seguimiento inmediato en el frontend.
     */
    private Long id;

    /**
     * El email registrado.
     * Se devuelve para confirmarle al usuario dónde debe buscar el correo.
     */
    private String email;

    /**
     * Nombre de pila.
     * Para personalizar el mensaje de éxito: "¡Genial, Juan! Ya casi terminamos..."
     */
    private String firstName;

    /**
     * Mensaje de éxito del sistema.
     * Ej: "Usuario registrado exitosamente. Por favor verifica tu correo electrónico."
     */
    private String message;

    /**
     * Timestamp de creación.
     */
    private LocalDateTime createdAt;
}
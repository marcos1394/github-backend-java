package com.quhealthy.auth_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO genérico para respuestas informativas de la API.
 *
 * USO:
 * Se utiliza en endpoints que ejecutan acciones (POST/PUT/DELETE)
 * y no necesitan devolver un objeto complejo, solo confirmar el éxito
 * o dar feedback al usuario.
 *
 * EJEMPLOS:
 * - "Correo verificado exitosamente."
 * - "Se ha enviado un enlace a tu correo."
 * - "Contraseña actualizada."
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageResponse {

    /**
     * El mensaje legible para el usuario final.
     * El Frontend puede mostrar esto directamente en un Toast o Alerta.
     */
    private String message;
}
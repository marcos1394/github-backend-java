package com.quhealthy.auth_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para ejecutar el cambio de contraseña.
 *
 * Flujo:
 * 1. El usuario recibió un email con un link que contiene un token (verifier).
 * 2. El Frontend captura ese token de la URL.
 * 3. El usuario escribe su nueva contraseña.
 * 4. Se envía este DTO para actualizar la credencial en la BD.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResetPasswordRequest {

    /**
     * El "Verifier" (token) recibido por email.
     * Este token valida que quien hace la petición es realmente el dueño del correo.
     */
    @NotBlank(message = "El token de restablecimiento es requerido")
    private String token;

    /**
     * La nueva contraseña deseada.
     *
     * 🔐 SEGURIDAD:
     * Debe cumplir exactamente los mismos requisitos que en el Registro
     * para mantener el estándar de seguridad de la plataforma.
     * - Mínimo 8 caracteres.
     * - Al menos una Mayúscula, una Minúscula y un Número.
     */
    @NotBlank(message = "La nueva contraseña es requerida")
    @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres")
    @Pattern(
            regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z]).{8,}$",
            message = "La contraseña debe contener al menos una mayúscula, una minúscula y un número"
    )
    private String newPassword;
}
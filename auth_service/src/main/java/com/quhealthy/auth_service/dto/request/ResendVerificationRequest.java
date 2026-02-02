package com.quhealthy.auth_service.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para solicitar el reenvío de códigos de verificación.
 *
 * Se utiliza cuando:
 * 1. El token original expiró.
 * 2. El usuario no recibió el correo/SMS original.
 *
 * Funciona tanto para Consumers como para Providers.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResendVerificationRequest {

    /**
     * El correo electrónico del usuario.
     * Se usa como identificador principal para buscar al usuario en ambas tablas.
     */
    @NotBlank(message = "El email es requerido")
    @Email(message = "El formato del correo electrónico no es válido")
    private String email;

    /**
     * El tipo de verificación que se desea reenviar.
     *
     * VALORES PERMITIDOS:
     * - "EMAIL": Reenvía el link de confirmación de cuenta.
     * - "SMS": Reenvía el código OTP de 6 dígitos al teléfono registrado.
     *
     * Validado por expresión regular para evitar inyecciones de valores basura.
     */
    @NotBlank(message = "El tipo de verificación es requerido")
    @Pattern(regexp = "^(EMAIL|SMS)$", message = "El tipo debe ser 'EMAIL' o 'SMS'")
    private String type;
}
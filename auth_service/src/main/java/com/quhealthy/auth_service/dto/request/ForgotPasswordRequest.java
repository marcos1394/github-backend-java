package com.quhealthy.auth_service.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para iniciar el flujo de recuperación de contraseña ("Olvidé mi contraseña").
 *
 * Flujo:
 * 1. El usuario ingresa su correo en la pantalla de Login.
 * 2. Este DTO llega al Backend.
 * 3. El Backend busca el correo en Providers y Consumers.
 * 4. Si existe, genera un token seguro (Reset Token) y envía un email con las instrucciones.
 *
 * Nota de Seguridad:
 * Si el correo NO existe, el endpoint debe responder "200 OK" (o un mensaje genérico)
 * de todas formas, para evitar el "Enumeration Attack" (que un hacker descubra qué
 * correos están registrados probando cuáles dan error y cuáles no).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ForgotPasswordRequest {

    @NotBlank(message = "El email es requerido")
    @Email(message = "El formato del correo electrónico no es válido")
    private String email;
}
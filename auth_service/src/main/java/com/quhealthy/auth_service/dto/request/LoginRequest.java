package com.quhealthy.auth_service.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para la Autenticación de Usuarios (Login).
 *
 * NOTA DE SEGURIDAD:
 * A diferencia del Registro, aquí NO validamos la complejidad de la contraseña (Regex)
 * para evitar bloquear usuarios antiguos si cambiamos las políticas de seguridad
 * y para no dar pistas a atacantes sobre las reglas de validación.
 * Solo validamos que los campos no vengan vacíos.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {

    @NotBlank(message = "El email es requerido")
    @Email(message = "El formato del correo electrónico no es válido")
    private String email;

    @NotBlank(message = "La contraseña es requerida")
    private String password;
}
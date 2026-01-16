package com.quhealthy.auth_service.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterProviderRequest {

    @NotBlank(message = "El nombre es obligatorio")
    private String name;

    @Email(message = "El formato del correo es inválido")
    @NotBlank(message = "El correo es obligatorio")
    private String email;

    @NotBlank(message = "El teléfono es obligatorio")
    private String phone;

    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = 6, message = "La contraseña debe tener al menos 6 caracteres")
    private String password;

    @NotBlank(message = "El tipo de servicio es obligatorio")
    private String serviceType; // 'health' o 'beauty'

    private boolean acceptTerms;

    private String referralCode; // Opcional
}
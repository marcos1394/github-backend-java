package com.quhealthy.auth_service.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {
    @NotBlank(message = "El identificador (email o teléfono) es obligatorio")
    private String identifier; // Puede ser email o teléfono

    @NotBlank(message = "La contraseña es obligatoria")
    private String password;
}
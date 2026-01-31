package com.quhealthy.auth_service.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SocialLoginRequest {
    @NotBlank(message = "El token de Google es obligatorio")
    private String token;

    // ✅ NUEVO: Campo vital para saber qué crear si el usuario no existe
    // Valores esperados: "PROVIDER" o "CONSUMER"
    @NotBlank(message = "El rol es obligatorio para el registro")
    private String role;
}
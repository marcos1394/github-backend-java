package com.quhealthy.auth_service.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SocialLoginRequest {
    @NotBlank(message = "El token de Google es obligatorio")
    private String token; // El 'idToken' que te da Google en el Front
}
package com.quhealthy.auth_service.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ResendVerificationRequest {
    
    @NotBlank(message = "El correo es obligatorio")
    @Email(message = "Formato de correo inválido")
    private String email;

    @NotBlank(message = "El tipo es obligatorio")
    @Pattern(regexp = "^(email|phone)$", message = "El tipo debe ser 'email' o 'phone'")
    private String type;
}
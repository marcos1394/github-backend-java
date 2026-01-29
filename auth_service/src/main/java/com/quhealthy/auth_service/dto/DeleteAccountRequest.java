package com.quhealthy.auth_service.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DeleteAccountRequest {
    @NotBlank(message = "La contraseña es obligatoria para confirmar la eliminación de la cuenta")
    private String password;
}
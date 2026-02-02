package com.quhealthy.auth_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para procesar la verificación de correo electrónico.
 *
 * Flujo:
 * 1. El usuario se registra -> Se genera un token UUID y se guarda en BD.
 * 2. Se envía un email con link: https://app.quhealthy.com/verify-email?token=XYZ
 * 3. El Frontend extrae 'XYZ' y lo envía en el body de esta petición.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerifyEmailRequest {

    /**
     * El token de verificación único.
     * Generalmente es un UUID v4 (36 caracteres), pero lo dejamos como String
     * para flexibilidad.
     *
     * El servicio buscará este token en:
     * - ConsumerRepository.findByEmailVerificationToken
     * - ProviderRepository.findByEmailVerificationToken
     */
    @NotBlank(message = "El token de verificación es requerido")
    private String token;
}
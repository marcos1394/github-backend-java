package com.quhealthy.auth_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para verificar el número de teléfono mediante código OTP (One-Time Password).
 *
 * Flujo:
 * 1. El sistema envía un SMS/WhatsApp con un código de 6 dígitos (ej: "849201").
 * 2. El usuario introduce ese código en el Frontend.
 * 3. Este DTO viaja al Backend para validar la correspondencia.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerifyPhoneRequest {

    /**
     * Código OTP de 6 dígitos.
     *
     * Validaciones:
     * - No puede estar vacío.
     * - Debe contener EXACTAMENTE 6 dígitos numéricos.
     * - Regex: ^\d{6}$
     */
    @NotBlank(message = "El código de verificación es requerido")
    @Pattern(regexp = "^\\d{6}$", message = "El código debe contener exactamente 6 dígitos numéricos")
    private String code;

    /**
     * Identificador del usuario (Email o Teléfono) que está intentando verificar.
     *
     * ¿Por qué es necesario?
     * Un código simple como "123456" podría generarse casualmente para dos usuarios distintos
     * en momentos diferentes. La validación segura requiere la combinación:
     * [USUARIO] + [CÓDIGO].
     *
     * Puede recibir:
     * - Email: "juan@example.com"
     * - Teléfono: "+526671234567"
     */
    @NotBlank(message = "El identificador (email o teléfono) es requerido")
    private String identifier;
}
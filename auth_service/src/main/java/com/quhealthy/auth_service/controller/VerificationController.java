package com.quhealthy.auth_service.controller;

import com.quhealthy.auth_service.dto.request.*;
import com.quhealthy.auth_service.dto.response.MessageResponse;
import com.quhealthy.auth_service.service.VerificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "3. Verificación y Seguridad", description = "Validación de cuentas y recuperación de contraseñas")
public class VerificationController {

    private final VerificationService verificationService;

    // ========================================================================
    // ✅ VALIDACIONES
    // ========================================================================

    @Operation(summary = "Verificar Email", description = "Valida el token enviado por correo al registrarse.")
    @PostMapping("/verify-email")
    public ResponseEntity<MessageResponse> verifyEmail(@RequestBody @Valid VerifyEmailRequest request) {
        return ResponseEntity.ok(verificationService.verifyEmail(request));
    }

    @Operation(summary = "Verificar Teléfono (OTP)", description = "Valida el código SMS de 6 dígitos.")
    @PostMapping("/verify-phone")
    public ResponseEntity<MessageResponse> verifyPhone(@RequestBody @Valid VerifyPhoneRequest request) {
        return ResponseEntity.ok(verificationService.verifyPhone(request));
    }

    @Operation(summary = "Reenviar Código/Link", description = "Reenvía el correo de activación o el SMS si expiraron.")
    @PostMapping("/resend-verification")
    public ResponseEntity<MessageResponse> resendVerification(@RequestBody @Valid ResendVerificationRequest request) {
        return ResponseEntity.ok(verificationService.resendVerification(request));
    }

    // ========================================================================
    // 🔑 PASSWORD RESET
    // ========================================================================

    @Operation(summary = "Olvidé mi contraseña", description = "Inicia el flujo de recuperación. Envía email con token.")
    @PostMapping("/forgot-password")
    public ResponseEntity<MessageResponse> forgotPassword(@RequestBody @Valid ForgotPasswordRequest request) {
        return ResponseEntity.ok(verificationService.forgotPassword(request));
    }

    @Operation(summary = "Restablecer contraseña", description = "Finaliza el flujo. Establece la nueva contraseña usando el token (selector:verifier).")
    @PostMapping("/reset-password")
    public ResponseEntity<MessageResponse> resetPassword(@RequestBody @Valid ResetPasswordRequest request) {
        return ResponseEntity.ok(verificationService.resetPassword(request));
    }
}
package com.quhealthy.auth_service.controller;

import com.quhealthy.auth_service.dto.AuthResponse;
import com.quhealthy.auth_service.dto.LoginRequest;
import com.quhealthy.auth_service.dto.ProviderStatusResponse;
import com.quhealthy.auth_service.dto.RegisterProviderRequest;
import com.quhealthy.auth_service.model.Provider;
import com.quhealthy.auth_service.dto.ResendVerificationRequest;
import com.quhealthy.auth_service.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import com.quhealthy.auth_service.dto.ForgotPasswordRequest;
import com.quhealthy.auth_service.dto.ResetPasswordRequest;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * Endpoint para Registro de Proveedores (Doctores/Especialistas).
     * Ruta: POST /api/auth/provider/register
     */
    @PostMapping("/provider/register")
    public ResponseEntity<AuthResponse> registerProvider(@Valid @RequestBody RegisterProviderRequest request) {
        
        // Llamada al servicio transaccional
        Provider newProvider = authService.registerProvider(request);
        
        // Construimos la respuesta "Enterprise" usando el DTO de respuesta
        AuthResponse response = AuthResponse.builder()
                .message("Registro exitoso. Se ha enviado un correo de verificación a " + newProvider.getEmail())
                .token(null) // No damos token aún, deben verificar email primero
                .partialToken(null)
                .status(AuthResponse.AuthStatus.builder()
                        .isEmailVerified(false)
                        .isPhoneVerified(false)
                        .onboardingComplete(false)
                        .hasActivePlan(true) // Tiene Trial
                        .build())
                .build();

        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    /**
     * Endpoint para Verificar Email.
     * GET /api/auth/verify-email?token=xyz
     */
    @GetMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(@RequestParam("token") String token) {
        try {
            String message = authService.verifyEmail(token);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", message
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }    

    /**
     * Endpoint de prueba (Health Check).
     */
    @GetMapping("/ping")
    public ResponseEntity<Map<String, String>> ping() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "OK");
        response.put("message", "QuHealthy Auth Service is running 🚀");
        return ResponseEntity.ok(response);
    }

    /**
     * Endpoint para Iniciar Sesión.
     * POST /api/auth/login
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        try {
            AuthResponse response = authService.login(request);
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                AuthResponse.builder()
                    .message(e.getMessage())
                    .build()
            );
        }
    }

    /**
     * 1. Solicitar Reseteo (Envía el correo)
     * POST /api/auth/forgot-password
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.requestPasswordReset(request);
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Si el correo existe, recibirás instrucciones para restablecer tu contraseña."
        ));
    }

    /**
     * 2. Ejecutar Reseteo (Cambia la contraseña)
     * POST /api/auth/reset-password
     */
    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        try {
            authService.resetPassword(request);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Contraseña actualizada correctamente."
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    /**
     * Endpoint Crítico: Obtener el contexto del usuario actual.
     * GET /api/auth/me
     * Header: Authorization: Bearer <token>
     */
    @GetMapping("/me")
    public ResponseEntity<ProviderStatusResponse> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String email = authentication.getName();
        Provider provider = authService.findByEmail(email);
        
        if (provider == null) {
            return ResponseEntity.notFound().build();
        }

        ProviderStatusResponse status = authService.getProviderStatus(provider.getId());
        return ResponseEntity.ok(status);
    }

    /**
     * Reenviar código de verificación (Email o SMS)
     * POST /api/auth/resend-verification
     */
    @PostMapping("/resend-verification")
    public ResponseEntity<?> resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        try {
            authService.resendVerification(request);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Si la cuenta existe y requiere verificación, se ha enviado el código."
            ));
            
        } catch (IllegalStateException | IllegalArgumentException e) {
            // Errores de lógica de negocio (Ya verificado, sin teléfono)
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        } catch (Exception e) {
            // ✅ CORREGIDO: Eliminamos el log y devolvemos 500 genérico
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "error", "Ocurrió un error al procesar la solicitud."
            ));
        }
    }
}
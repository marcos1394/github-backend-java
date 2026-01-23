package com.quhealthy.auth_service.controller;

import com.quhealthy.auth_service.dto.AuthResponse;
import com.quhealthy.auth_service.dto.LoginRequest;
import com.quhealthy.auth_service.dto.RegisterProviderRequest;
import com.quhealthy.auth_service.model.Provider;
import com.quhealthy.auth_service.dto.VerifyPhoneRequest; // Asegúrate de tener este import
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
import com.quhealthy.auth_service.dto.RegisterConsumerRequest;
import com.quhealthy.auth_service.model.Consumer;
import com.quhealthy.auth_service.dto.SocialLoginRequest;
import com.quhealthy.auth_service.dto.UserContextResponse; // 👈 IMPORTANTE
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
     * Endpoint Crítico: Obtener el contexto del usuario actual (Polimórfico).
     * GET /api/auth/me
     * Header: Authorization: Bearer <token>
     * * Retorna: UserContextResponse (que puede contener data de Provider o Consumer)
     */
    @GetMapping("/me")
    public ResponseEntity<UserContextResponse> getCurrentUser() {
        
        // 1. Obtener la autenticación del contexto de seguridad
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        // 2. Validar que no sea anónimo
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String email = authentication.getName(); // El email viene del Token JWT
        
        // 3. Llamar al servicio "inteligente" que busca en ambas tablas
        // Ya no buscamos "Provider", buscamos "Contexto"
        UserContextResponse context = authService.getUserContext(email);
        
        // 4. Si no encuentra ni proveedor ni consumidor, es un 404
        if (context == null) {
            return ResponseEntity.notFound().build();
        }

        // 5. Devolver la respuesta unificada
        return ResponseEntity.ok(context);
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

    /**
     * Verificar código SMS
     * POST /api/auth/verify-phone
     * Requiere Header: Authorization: Bearer <token>
     */
    @PostMapping("/verify-phone")
    public ResponseEntity<?> verifyPhone(@Valid @RequestBody VerifyPhoneRequest request) {
        // 1. Obtener quien hace la petición desde el JWT (Seguridad)
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        // Validación extra de seguridad (aunque el filtro JWT ya lo hace)
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String email = authentication.getName(); // El email viene en el "subject" del token

        try {
            // 2. Llamar al servicio
            authService.verifyPhone(email, request.getCode());

            // 3. Respuesta de Éxito
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Teléfono verificado correctamente."
            ));

        } catch (IllegalStateException | IllegalArgumentException e) {
            // 4. Errores de negocio (Código mal, expirado, ya verificado) -> 400 Bad Request
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        } catch (Exception e) {
            // 5. Error inesperado -> 500 Internal Server Error
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "error", "Ocurrió un error al verificar el teléfono."
            ));
        }
    }

    /**
     * Endpoint para Registro de Consumidores (Pacientes).
     * Ruta: POST /api/auth/consumer/register
     */
    @PostMapping("/consumer/register")
    public ResponseEntity<AuthResponse> registerConsumer(@Valid @RequestBody RegisterConsumerRequest request) {
        
        // 1. Llamada al servicio (Guarda en BD y envía email)
        Consumer newConsumer = authService.registerConsumer(request);
        
        // 2. Construimos la respuesta USANDO los datos reales del objeto guardado
        AuthResponse response = AuthResponse.builder()
                .message("Registro exitoso. Hemos enviado un correo de verificación a " + newConsumer.getEmail()) // ✅ Usamos el email real
                .token(null) // Seguridad: No damos token hasta que verifique
                .status(AuthResponse.AuthStatus.builder()
                        .isEmailVerified(newConsumer.isEmailVerified()) // ✅ Usamos el estado real (false)
                        .isPhoneVerified(false) // Los consumers por defecto no piden teléfono al inicio
                        .onboardingComplete(true) // Asumimos true por defecto para pacientes
                        .hasActivePlan(false)
                        .build())
                .build();

        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    /**
     * Endpoint para Login/Registro con Google.
     * POST /api/auth/social/google
     */
    @PostMapping("/social/google")
    public ResponseEntity<AuthResponse> googleLogin(@Valid @RequestBody SocialLoginRequest request) {
        // Llama al método polimórfico que ya implementaste en el servicio
        AuthResponse response = authService.authenticateWithGoogle(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Endpoint para Login/Registro con Facebook.
     * POST /api/auth/social/facebook
     */
    @PostMapping("/social/facebook")
    public ResponseEntity<AuthResponse> facebookLogin(@Valid @RequestBody SocialLoginRequest request) {
        AuthResponse response = authService.authenticateWithFacebook(request);
        return ResponseEntity.ok(response);
    }
}
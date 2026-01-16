package com.quhealthy.auth_service.controller;

import com.quhealthy.auth_service.dto.AuthResponse;
import com.quhealthy.auth_service.dto.RegisterProviderRequest;
import com.quhealthy.auth_service.model.Provider;
import com.quhealthy.auth_service.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
     * Endpoint de prueba para verificar que el servidor responde (Health Check).
     */
    @GetMapping("/ping")
    public ResponseEntity<Map<String, String>> ping() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "OK");
        response.put("message", "QuHealthy Auth Service is running 🚀");
        return ResponseEntity.ok(response);
    }
}
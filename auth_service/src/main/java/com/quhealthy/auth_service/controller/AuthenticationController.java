package com.quhealthy.auth_service.controller;

import com.quhealthy.auth_service.dto.request.LoginRequest;
import com.quhealthy.auth_service.dto.response.AuthResponse;
import com.quhealthy.auth_service.service.AuthenticationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "2. Autenticación", description = "Login y gestión de tokens JWT")
public class AuthenticationController {

    private final AuthenticationService authenticationService;

    @Operation(summary = "Iniciar Sesión", description = "Autentica usuarios (Consumer o Provider) y devuelve tokens JWT + Estado de cuenta.")
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody @Valid LoginRequest request) {
        log.info("Recibida petición de login para: {}", request.getEmail());
        return ResponseEntity.ok(authenticationService.authenticate(request));
    }

    // Aquí iría el endpoint /refresh-token en el futuro
}
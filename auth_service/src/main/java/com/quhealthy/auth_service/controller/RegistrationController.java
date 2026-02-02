package com.quhealthy.auth_service.controller;

import com.quhealthy.auth_service.dto.request.RegisterConsumerRequest;
import com.quhealthy.auth_service.dto.request.RegisterProviderRequest;
import com.quhealthy.auth_service.dto.response.ConsumerRegistrationResponse;
import com.quhealthy.auth_service.dto.response.ProviderRegistrationResponse;
import com.quhealthy.auth_service.service.RegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus; // <--- Import necesario
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth/register")
@RequiredArgsConstructor
@Tag(name = "1. Registro", description = "Endpoints para la creación de nuevas cuentas")
public class RegistrationController {

    private final RegistrationService registrationService;

    @Operation(summary = "Registrar Paciente", description = "Crea una nueva cuenta de tipo CONSUMER.")
    @PostMapping("/consumer")
    public ResponseEntity<ConsumerRegistrationResponse> registerConsumer(@RequestBody @Valid RegisterConsumerRequest request) {
        ConsumerRegistrationResponse response = registrationService.registerConsumer(request);
        // ✅ Retorna 201 Created en lugar de 200 OK
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Registrar Profesional", description = "Crea una nueva cuenta de tipo PROVIDER (Solo datos básicos e industria).")
    @PostMapping("/provider")
    public ResponseEntity<ProviderRegistrationResponse> registerProvider(@RequestBody @Valid RegisterProviderRequest request) {
        ProviderRegistrationResponse response = registrationService.registerProvider(request);
        // ✅ Retorna 201 Created en lugar de 200 OK
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
package com.quhealthy.appointment_service.controller;

import com.quhealthy.appointment_service.dto.request.CompleteAppointmentRequest;
import com.quhealthy.appointment_service.dto.request.CreateAppointmentRequest;
import com.quhealthy.appointment_service.dto.request.RescheduleRequest;
import com.quhealthy.appointment_service.dto.response.AppointmentResponse;
import com.quhealthy.appointment_service.service.AppointmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/appointments")
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentService appointmentService;

    // =================================================================
    // 👤 ZONA DE PACIENTES (Consumers)
    // =================================================================

    /**
     * ✅ AGENDAR CITA
     * Endpoint: POST /api/appointments/book
     * Body: { providerId: 1, serviceId: 5, startTime: "...", paymentMethod: "CASH" }
     */
    @PostMapping("/book")
    public ResponseEntity<AppointmentResponse> createAppointment(
            @AuthenticationPrincipal Long consumerId, // 👈 ID extraído del Token (Seguro)
            @Valid @RequestBody CreateAppointmentRequest request) {
        
        log.info("📝 Solicitud de cita recibida por Consumer ID: {}", consumerId);
        AppointmentResponse response = appointmentService.createAppointment(consumerId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * ✅ MIS CITAS (Historial Paciente)
     * Endpoint: GET /api/appointments/consumer
     */
    @GetMapping("/consumer")
    public ResponseEntity<Page<AppointmentResponse>> getConsumerAppointments(
            @AuthenticationPrincipal Long consumerId,
            @PageableDefault(size = 10, sort = "startTime", direction = Sort.Direction.DESC) Pageable pageable) {
        
        return ResponseEntity.ok(appointmentService.getMyAppointments(consumerId, false, pageable));
    }

    // =================================================================
    // 👨‍⚕️ ZONA DE PROVEEDORES (Doctors)
    // =================================================================

    /**
     * ✅ COMPLETAR CITA (Finalizar Consulta)
     * Endpoint: PUT /api/appointments/{id}/complete
     * Acción: Cambia status a COMPLETED y dispara evento de Reseña.
     */
    @PutMapping("/{id}/complete")
    public ResponseEntity<AppointmentResponse> completeAppointment(
            @AuthenticationPrincipal Long providerId,
            @PathVariable Long id,
            @Valid @RequestBody CompleteAppointmentRequest request) {
        
        log.info("✅ Doctor ID: {} completando cita ID: {}", providerId, id);
        return ResponseEntity.ok(appointmentService.completeAppointment(providerId, id, request));
    }

    /**
     * ✅ AGENDA DEL DOCTOR
     * Endpoint: GET /api/appointments/provider
     */
    @GetMapping("/provider")
    public ResponseEntity<Page<AppointmentResponse>> getProviderAppointments(
            @AuthenticationPrincipal Long providerId,
            @PageableDefault(size = 20, sort = "startTime", direction = Sort.Direction.ASC) Pageable pageable) {
        
        return ResponseEntity.ok(appointmentService.getMyAppointments(providerId, true, pageable));
    }

    // =================================================================
    // 🔄 ZONA COMÚN (Ambos roles)
    // =================================================================

    /**
     * ✅ CANCELAR CITA
     * Endpoint: PUT /api/appointments/{id}/cancel
     * Nota: El servicio detecta si es Doctor o Paciente y aplica reglas de penalización.
     */
    @PutMapping("/{id}/cancel")
    public ResponseEntity<AppointmentResponse> cancelAppointment(
            @AuthenticationPrincipal Long userId,
            Authentication authentication, // Objeto completo para sacar el ROL
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "Sin motivo específico") String reason) {
        
        String role = getRoleFromAuth(authentication);
        log.info("🚫 Cancelación solicitada por {} ID: {} - Motivo: {}", role, userId, reason);
        
        return ResponseEntity.ok(appointmentService.cancelAppointment(userId, role, id, reason));
    }

    /**
     * ✅ REAGENDAR
     * Endpoint: PUT /api/appointments/{id}/reschedule
     */
    @PutMapping("/{id}/reschedule")
    public ResponseEntity<AppointmentResponse> rescheduleAppointment(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @Valid @RequestBody RescheduleRequest request) {
        
        log.info("🔄 Reagendamiento solicitado por ID: {}", userId);
        return ResponseEntity.ok(appointmentService.rescheduleAppointment(userId, id, request));
    }

    // =================================================================
    // 🛠️ HELPERS
    // =================================================================

    /**
     * Extrae el rol principal (PROVIDER o PATIENT/CONSUMER) del token.
     */
    private String getRoleFromAuth(Authentication auth) {
        if (auth == null) return "UNKNOWN";
        
        // Buscamos si tiene el rol de proveedor
        for (GrantedAuthority authority : auth.getAuthorities()) {
            String role = authority.getAuthority(); // Ej: "ROLE_PROVIDER"
            if (role.contains("PROVIDER")) {
                return "ROLE_PROVIDER";
            }
        }
        return "ROLE_CONSUMER"; // Default
    }
}
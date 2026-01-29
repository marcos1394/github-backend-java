package com.quhealthy.appointment_service.controller;

import com.quhealthy.appointment_service.model.ProviderSchedule;
import com.quhealthy.appointment_service.model.TimeBlock;
import com.quhealthy.appointment_service.service.CalendarService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/calendar")
@RequiredArgsConstructor
public class CalendarController {

    private final CalendarService calendarService;

    // ========================================================================
    // 🌍 ENDPOINTS PÚBLICOS (O CONSUMIBLES POR PACIENTES)
    // ========================================================================

    /**
     * ✅ 1. CONSULTAR DISPONIBILIDAD (SLOTS)
     * GET /api/calendar/availability/provider/{id}?start=2024-01-01&end=2024-01-31&duration=30
     * * Calcula dinámicamente qué huecos tiene libres el doctor, restando citas y vacaciones.
     * Retorna: ["2024-01-01T09:00:00", "2024-01-01T09:30:00", ...]
     */
    @GetMapping("/availability/provider/{providerId}")
    public ResponseEntity<List<String>> getAvailability(
            @PathVariable Long providerId,
            @RequestParam("start") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam("end") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(value = "duration", defaultValue = "30") int durationMinutes) {

        log.info("📅 Buscando disponibilidad para Provider {} del {} al {}", providerId, startDate, endDate);
        
        List<LocalDateTime> slots = calendarService.getAvailableSlots(providerId, startDate, endDate, durationMinutes);
        
        // Transformamos a String ISO para que el JSON sea ligero y estándar
        List<String> isoSlots = slots.stream()
                .map(LocalDateTime::toString)
                .toList();

        return ResponseEntity.ok(isoSlots);
    }

    // ========================================================================
    // 🔐 ENDPOINTS PROTEGIDOS (SOLO PARA EL DOCTOR DUEÑO DE LA AGENDA)
    // ========================================================================

    /**
     * ✅ 2. CONFIGURAR HORARIO BASE (Wipe & Replace)
     * PUT /api/calendar/schedule
     * * El doctor define su semana típica. Ej: "Trabajo Lunes 9-5 y Martes 10-2".
     * Se usa PUT porque reemplaza la configuración anterior completa.
     */
    @PutMapping("/schedule")
    public ResponseEntity<List<ProviderSchedule>> updateSchedule(
            @AuthenticationPrincipal Long providerId, // Seguridad: Obtenemos ID del Token
            @RequestBody List<ProviderSchedule> schedules) {
        
        if (providerId == null) {
            return ResponseEntity.status(401).build();
        }

        log.info("🛠️ Actualizando configuración de horario para Provider: {}", providerId);
        
        List<ProviderSchedule> updated = calendarService.updateOperatingHours(providerId, schedules);
        return ResponseEntity.ok(updated);
    }

    /**
     * ✅ 3. VER MI HORARIO ACTUAL
     * GET /api/calendar/schedule
     * Sirve para pintar el formulario de "Configuración de Agenda" en el frontend.
     */
    @GetMapping("/schedule")
    public ResponseEntity<List<ProviderSchedule>> getMySchedule(
            @AuthenticationPrincipal Long providerId) {
        
        if (providerId == null) {
            return ResponseEntity.status(401).build();
        }
        
        return ResponseEntity.ok(calendarService.getProviderSchedules(providerId));
    }

    /**
     * ✅ 4. CREAR BLOQUEO DE TIEMPO (Vacaciones/Cierre)
     * POST /api/calendar/block
     * * El doctor dice: "El viernes me voy temprano" o "Vacaciones de Navidad".
     * Esto resta disponibilidad en el cálculo del endpoint #1.
     */
    @PostMapping("/block")
    public ResponseEntity<TimeBlock> createTimeBlock(
            @AuthenticationPrincipal Long providerId,
            @RequestBody TimeBlock blockData) {
        
        if (providerId == null) {
            return ResponseEntity.status(401).build();
        }

        log.info("⛔ Creando bloqueo de tiempo para Provider: {}", providerId);
        
        // Seguridad: Forzamos que el bloqueo sea del usuario logueado
        blockData.setProviderId(providerId);
        
        TimeBlock created = calendarService.createTimeBlock(blockData);
        return ResponseEntity.ok(created);
    }
}
package com.quhealthy.appointment_service.controller;

import com.quhealthy.appointment_service.service.GoogleCalendarService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/appointments/calendar")
@RequiredArgsConstructor
public class CalendarSyncController {

    private final GoogleCalendarService calendarService;

    /**
     * Paso 1: Iniciar el proceso de conexión.
     * Retorna la URL de Google para que el doctor se loguee.
     */
    @PostMapping("/connect")
    public ResponseEntity<Map<String, String>> connectCalendar(@AuthenticationPrincipal Long providerId) {
        log.info("Iniciando conexión de Google Calendar para provider: {}", providerId);
        // ✅ CORREGIDO: Usamos el nombre correcto del método del servicio
        String url = calendarService.getAuthorizationUrl(providerId);
        return ResponseEntity.ok(Map.of("url", url));
    }

    /**
     * Paso 2: Google nos devuelve el control aquí (Callback).
     * Intercambiamos el código por tokens.
     */
    @GetMapping("/callback")
    public ResponseEntity<String> handleCallback(
            @RequestParam("code") String code,
            @RequestParam("state") String state) {
        
        log.info("Recibido callback de Google. State (ProviderId): {}", state);
        try {
            Long providerId = Long.valueOf(state);
            // ✅ CORREGIDO: Usamos el nombre correcto del método del servicio
            calendarService.exchangeCodeForTokens(code, providerId);
            return ResponseEntity.ok("Google Calendar conectado exitosamente. Puedes cerrar esta ventana.");
        } catch (IOException e) {
            log.error("Error en callback de Google", e);
            return ResponseEntity.status(500).body("Error conectando con Google Calendar.");
        }
    }

    /**
     * Paso 3 (Opcional): Forzar una sincronización manual.
     */
    @PostMapping("/sync")
    public ResponseEntity<Void> manualSync(@AuthenticationPrincipal Long providerId) {
        log.info("Solicitud de sincronización manual para provider: {}", providerId);
        try {
            // ✅ CORREGIDO: Ahora este método existirá en el servicio
            calendarService.syncCalendar(providerId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
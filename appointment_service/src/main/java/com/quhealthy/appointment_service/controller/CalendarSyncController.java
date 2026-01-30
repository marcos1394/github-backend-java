package com.quhealthy.appointment_service.controller;

import com.quhealthy.appointment_service.service.GoogleCalendarService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/appointments/google")
@RequiredArgsConstructor
public class CalendarSyncController {

    private final GoogleCalendarService calendarService;

    @GetMapping("/connect")
    public ResponseEntity<?> connect(@AuthenticationPrincipal Long providerId) {
        String url = calendarService.generateAuthUrl(providerId);
        return ResponseEntity.ok(Map.of("url", url));
    }

    @GetMapping("/callback")
    public void callback(@RequestParam String code, @RequestParam String state, HttpServletResponse response) throws IOException {
        try {
            calendarService.handleCallback(code, state);
            response.sendRedirect("https://quhealthy.com/profile/calendar?status=success");
        } catch (Exception e) {
            response.sendRedirect("https://quhealthy.com/profile/calendar?status=error");
        }
    }
    
    @PostMapping("/sync")
    public ResponseEntity<?> forceSync(@AuthenticationPrincipal Long providerId) throws IOException {
        calendarService.syncCalendar(providerId);
        return ResponseEntity.ok(Map.of("message", "Sincronización iniciada"));
    }
}
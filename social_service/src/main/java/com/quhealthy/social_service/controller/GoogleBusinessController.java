package com.quhealthy.social_service.controller;

import com.quhealthy.social_service.service.GoogleBusinessService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/social/google")
@RequiredArgsConstructor
public class GoogleBusinessController {

    private final GoogleBusinessService googleService;

    // 1. Frontend llama aquí cuando el doctor da click en "Conectar Google"
    @GetMapping("/connect")
    public ResponseEntity<?> connectGoogle(@AuthenticationPrincipal Long providerId) {
        String url = googleService.generateAuthUrl(providerId);
        return ResponseEntity.ok(Map.of("url", url));
    }

    // 2. Google redirige aquí después del login
    @GetMapping("/callback")
    public void handleCallback(
            @RequestParam String code,
            @RequestParam String state,
            HttpServletResponse response
    ) throws IOException {
        try {
            googleService.handleCallback(code, state);
            // Redirigir al frontend con éxito
            response.sendRedirect("https://quhealthy.com/profile/social?status=success_google");
        } catch (Exception e) {
            response.sendRedirect("https://quhealthy.com/profile/social?status=error&msg=" + e.getMessage());
        }
    }
}
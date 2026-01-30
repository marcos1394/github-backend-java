package com.quhealthy.social_service.controller;

import com.quhealthy.social_service.service.YouTubeService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/social/youtube")
@RequiredArgsConstructor
public class YouTubeController {

    private final YouTubeService youTubeService;

    /**
     * Paso 1: El frontend llama aquí para obtener la URL de login de Google.
     */
    @GetMapping("/connect")
    public ResponseEntity<?> connectYouTube(@AuthenticationPrincipal Long providerId) {
        String url = youTubeService.generateAuthUrl(providerId);
        return ResponseEntity.ok(Map.of("url", url));
    }

    /**
     * Paso 2: Google redirige aquí después de que el usuario acepta.
     */
    @GetMapping("/callback")
    public void handleCallback(
            @RequestParam String code,
            @RequestParam String state,
            HttpServletResponse response
    ) throws IOException {
        try {
            youTubeService.handleCallback(code, state);
            // Redirigimos al frontend con éxito
            response.sendRedirect("https://quhealthy.com/profile/social?status=success_youtube");
        } catch (Exception e) {
            log.error("Error en callback de YouTube: {}", e.getMessage());
            response.sendRedirect("https://quhealthy.com/profile/social?status=error&msg=" + e.getMessage());
        }
    }
}
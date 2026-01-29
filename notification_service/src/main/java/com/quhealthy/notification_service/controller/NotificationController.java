package com.quhealthy.notification_service.controller;

import com.quhealthy.notification_service.dto.NotificationResponse;
import com.quhealthy.notification_service.dto.UnreadCountResponse;
import com.quhealthy.notification_service.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * ✅ OBTENER HISTORIAL (Paginado)
     * GET /api/notifications?page=0&size=10
     */
    @GetMapping
    public ResponseEntity<Page<NotificationResponse>> getMyNotifications(
            @AuthenticationPrincipal Long userId, // Extraído del JWT
            Authentication authentication,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        
        String role = extractRoleFromAuth(authentication);
        log.debug("📩 Consultando notificaciones para Usuario: {} Rol: {}", userId, role);
        
        return ResponseEntity.ok(notificationService.getUserNotifications(userId, role, pageable));
    }

    /**
     * ✅ OBTENER CONTADOR NO LEÍDAS (Badge 🔴)
     * GET /api/notifications/unread-count
     * Ideal para llamar en el Navbar cada X segundos o al cargar la app.
     */
    @GetMapping("/unread-count")
    public ResponseEntity<UnreadCountResponse> getUnreadCount(
            @AuthenticationPrincipal Long userId,
            Authentication authentication) {
        
        String role = extractRoleFromAuth(authentication);
        return ResponseEntity.ok(notificationService.getUnreadCount(userId, role));
    }

    /**
     * ✅ MARCAR UNA COMO LEÍDA
     * PUT /api/notifications/{id}/read
     * Se llama cuando el usuario hace click en una notificación específica.
     */
    @PutMapping("/{id}/read")
    public ResponseEntity<Void> markOneAsRead(
            @PathVariable Long id,
            @AuthenticationPrincipal Long userId) {
        
        notificationService.markOneAsRead(id, userId);
        return ResponseEntity.noContent().build(); // 204 No Content
    }

    /**
     * ✅ MARCAR TODAS COMO LEÍDAS
     * PUT /api/notifications/read-all
     * Botón "Marcar todo como leído" en la bandeja.
     */
    @PutMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead(
            @AuthenticationPrincipal Long userId,
            Authentication authentication) {
        
        String role = extractRoleFromAuth(authentication);
        notificationService.markAllAsRead(userId, role);
        
        return ResponseEntity.noContent().build();
    }

    // =================================================================
    // 🛠️ HELPERS PRIVADOS
    // =================================================================

    /**
     * Extrae el rol limpio ("PROVIDER" o "CONSUMER") del objeto de autenticación.
     * Spring Security suele guardar "ROLE_PROVIDER", así que limpiamos el prefijo.
     */
    private String extractRoleFromAuth(Authentication auth) {
        if (auth == null || auth.getAuthorities().isEmpty()) {
            throw new SecurityException("Usuario no autenticado o sin roles.");
        }

        // Buscamos el primer rol que coincida con nuestra lógica
        for (GrantedAuthority authority : auth.getAuthorities()) {
            String role = authority.getAuthority(); // Ej: "ROLE_PROVIDER"
            
            if (role.contains("PROVIDER")) return "PROVIDER";
            if (role.contains("CONSUMER") || role.contains("PATIENT")) return "CONSUMER";
            if (role.contains("ADMIN")) return "ADMIN";
        }

        // Fallback por seguridad
        throw new SecurityException("Rol de usuario no reconocido para notificaciones.");
    }
}
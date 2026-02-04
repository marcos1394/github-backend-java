package com.quhealthy.notification_service.controller;

import com.quhealthy.notification_service.dto.NotificationResponse;
import com.quhealthy.notification_service.dto.UnreadCountResponse;
import com.quhealthy.notification_service.model.enums.TargetRole;
import com.quhealthy.notification_service.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Tag(
        name = "Notificaciones In-App",
        description = "Endpoints para la bandeja de entrada de notificaciones del usuario (Campanita)"
)
public class NotificationController {

    private final NotificationService notificationService;

    // =================================================================
    // 📨 BANDEJA DE ENTRADA (Lectura)
    // =================================================================

    @Operation(
            summary = "Obtener historial",
            description = "Devuelve las notificaciones paginadas del usuario autenticado."
    )
    @GetMapping
    public ResponseEntity<Page<NotificationResponse>> getMyNotifications(
            Authentication authentication,
            @PageableDefault(
                    size = 20,
                    sort = "createdAt",
                    direction = Sort.Direction.DESC
            ) Pageable pageable
    ) {
        Long userId = extractUserId(authentication);
        TargetRole role = extractRoleFromAuth(authentication);

        log.debug("📩 Consultando inbox para Usuario: {} Rol: {}", userId, role);

        return ResponseEntity.ok(
                notificationService.getUserNotifications(userId, role, pageable)
        );
    }

    @Operation(
            summary = "Contador de No Leídas",
            description = "Devuelve el número para el badge rojo (🔴) de la UI."
    )
    @GetMapping("/unread-count")
    public ResponseEntity<UnreadCountResponse> getUnreadCount(
            Authentication authentication
    ) {
        Long userId = extractUserId(authentication);
        TargetRole role = extractRoleFromAuth(authentication);

        return ResponseEntity.ok(
                notificationService.getUnreadCount(userId, role)
        );
    }

    // =================================================================
    // 🖱️ ACCIONES (Escritura / Estado)
    // =================================================================

    @Operation(
            summary = "Marcar una como leída",
            description = "Se llama cuando el usuario hace click en una notificación específica."
    )
    @PutMapping("/{id}/read")
    public ResponseEntity<Void> markOneAsRead(
            @PathVariable Long id,
            Authentication authentication
    ) {
        Long userId = extractUserId(authentication);
        TargetRole role = extractRoleFromAuth(authentication);

        notificationService.markOneAsRead(id, userId, role);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Marcar todas como leídas",
            description = "Botón 'Marcar todo como leído' para limpiar la bandeja."
    )
    @PutMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead(
            Authentication authentication
    ) {
        Long userId = extractUserId(authentication);
        TargetRole role = extractRoleFromAuth(authentication);

        notificationService.markAllAsRead(userId, role);
        return ResponseEntity.noContent().build();
    }

    // =================================================================
    // 🛠️ HELPERS PRIVADOS
    // =================================================================

    /**
     * Extrae el userId del Authentication.
     * En tests: viene de MockMvc.principal(...)
     * En prod: viene del JwtAuthenticationToken / CustomPrincipal
     */
    private Long extractUserId(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new IllegalStateException("Usuario no autenticado");
        }

        Object principal = authentication.getPrincipal();

        if (principal instanceof Long id) {
            return id;
        }

        throw new IllegalStateException(
                "Principal no soportado: " + principal.getClass().getName()
        );
    }

    /**
     * Extrae el rol limpio y tipado (Enum) del objeto de autenticación.
     * Convierte "ROLE_PROVIDER" -> TargetRole.PROVIDER
     */
    private TargetRole extractRoleFromAuth(Authentication auth) {
        if (auth == null || auth.getAuthorities() == null || auth.getAuthorities().isEmpty()) {
            log.warn("⚠️ Usuario autenticado sin roles. Asignando CONSUMER por defecto.");
            return TargetRole.CONSUMER;
        }

        for (GrantedAuthority authority : auth.getAuthorities()) {
            String role = authority.getAuthority().toUpperCase();

            if (role.contains("PROVIDER")) return TargetRole.PROVIDER;
            if (role.contains("CONSUMER") || role.contains("PATIENT")) return TargetRole.CONSUMER;
            if (role.contains("ADMIN")) return TargetRole.ADMIN;
        }

        log.warn("⚠️ Rol desconocido en JWT: {}. Asignando CONSUMER.", auth.getAuthorities());
        return TargetRole.CONSUMER;
    }
}

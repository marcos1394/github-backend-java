package com.quhealthy.social_service.controller;

import com.quhealthy.social_service.dto.SchedulePostRequest;
import com.quhealthy.social_service.dto.SocialConnectionResponse;
import com.quhealthy.social_service.model.ScheduledPost;
import com.quhealthy.social_service.model.SocialConnection;
import com.quhealthy.social_service.model.enums.PostStatus;
import com.quhealthy.social_service.repository.ScheduledPostRepository;
import com.quhealthy.social_service.repository.SocialConnectionRepository;
import com.quhealthy.social_service.service.ai.CloudStorageService;
import com.quhealthy.social_service.service.publishing.SocialAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/social")
@RequiredArgsConstructor
public class SocialController {

    private final SocialAuthService socialAuthService;
    private final SocialConnectionRepository connectionRepository;
    private final ScheduledPostRepository scheduledPostRepository;
    private final CloudStorageService cloudStorageService;

    // =================================================================
    // 1. GESTIÓN DE CONEXIONES (Vincular cuentas)
    // =================================================================

    /**
     * Endpoint que llama el Front después del login con Facebook/Instagram.
     * Recibe el 'code' y lo canjea por un Token de larga duración.
     */
    @PostMapping("/auth/facebook/callback")
    public ResponseEntity<?> linkFacebook(@RequestBody Map<String, String> payload) {
        String code = payload.get("code");
        if (code == null) return ResponseEntity.badRequest().body("Falta el código de autorización.");

        Long providerId = getAuthenticatedUserId();
        
        try {
            socialAuthService.handleFacebookCallback(providerId, code);
            return ResponseEntity.ok(Map.of("message", "Facebook vinculado exitosamente"));
        } catch (Exception e) {
            log.error("Error vinculando Facebook: ", e);
            return ResponseEntity.badRequest().body("Error al vincular con Facebook: " + e.getMessage());
        }
    }

    /**
     * Lista todas las cuentas conectadas del usuario (sin mostrar tokens).
     */
    @GetMapping("/connections")
    public ResponseEntity<List<SocialConnectionResponse>> getConnections() {
        Long providerId = getAuthenticatedUserId();

        List<SocialConnectionResponse> connections = connectionRepository.findByProviderId(providerId)
                .stream()
                .map(conn -> SocialConnectionResponse.builder()
                        .id(conn.getId())
                        .platform(conn.getPlatform())
                        .platformUserName(conn.getPlatformUserName())
                        .profileImageUrl(conn.getProfileImageUrl())
                        .isConnected(true)
                        .connectedAt(conn.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        return ResponseEntity.ok(connections);
    }

    /**
     * Desvincular una cuenta (Borrar token).
     */
    @DeleteMapping("/connections/{id}")
    public ResponseEntity<?> deleteConnection(@PathVariable UUID id) {
        Long providerId = getAuthenticatedUserId();
        
        // Verificamos que la conexión pertenezca al usuario que la quiere borrar
        SocialConnection conn = connectionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Conexión no encontrada"));

        if (!conn.getProviderId().equals(providerId)) {
            return ResponseEntity.status(403).body("No tienes permiso para eliminar esta conexión.");
        }

        connectionRepository.delete(conn);
        return ResponseEntity.ok(Map.of("message", "Cuenta desvinculada correctamente"));
    }

    // =================================================================
    // 2. GESTIÓN DE POSTS (Scheduling)
    // =================================================================

    /**
     * Agendar un nuevo post.
     * Guarda en BD con estado 'SCHEDULED'. El Cron Job lo recogerá después.
     */
    @PostMapping("/posts/schedule")
    public ResponseEntity<?> schedulePost(@Valid @RequestBody SchedulePostRequest request) {
        Long providerId = getAuthenticatedUserId();

        // Validar que la conexión existe y es mía
        SocialConnection conn = connectionRepository.findById(request.getSocialConnectionId())
                .orElseThrow(() -> new RuntimeException("La conexión social seleccionada no existe."));

        if (!conn.getProviderId().equals(providerId)) {
            return ResponseEntity.status(403).body("No te pertenece esa conexión social.");
        }

        // Crear el Post
        ScheduledPost post = ScheduledPost.builder()
                .providerId(providerId)
                .socialConnection(conn)
                .content(request.getContent())
                .mediaUrls(request.getMediaUrls()) // Lista de URLs de GCS
                .scheduledAt(request.getScheduledAt())
                .status(PostStatus.SCHEDULED)
                .generatedByAi(false) // Por ahora manual, luego conectamos Gemini
                .build();

        scheduledPostRepository.save(post);

        return ResponseEntity.ok(Map.of("message", "Post programado exitosamente", "postId", post.getId()));
    }

    /**
     * Obtener el calendario de posts (Historial y Futuros).
     */
    @GetMapping("/posts")
    public ResponseEntity<List<ScheduledPost>> getPosts() {
        Long providerId = getAuthenticatedUserId();
        // Nota: En un sistema real, usaríamos Paginación (Pageable) aquí.
        // Para el MVP devolvemos la lista completa ordenada.
        return ResponseEntity.ok(scheduledPostRepository.findByProviderIdOrderByScheduledAtDesc(providerId));
    }

    // =================================================================
    // 3. UTILIDADES (Uploads)
    // =================================================================

    /**
     * Subir una imagen/video a Google Cloud Storage antes de postear.
     * Devuelve la URL pública.
     */
    @PostMapping("/upload")
    public ResponseEntity<?> uploadMedia(@RequestParam("file") MultipartFile file) {
        try {
            // Validamos tipo de archivo básico
            String contentType = file.getContentType();
            if (contentType == null || (!contentType.startsWith("image/") && !contentType.startsWith("video/"))) {
                return ResponseEntity.badRequest().body("Solo se permiten imágenes o videos.");
            }

            // Llamamos al servicio de Storage
            String publicUrl = cloudStorageService.uploadFile(
                    file.getBytes(),
                    contentType,
                    "social-media-uploads" // Carpeta en el bucket
            );

            return ResponseEntity.ok(Map.of("url", publicUrl));

        } catch (IOException e) {
            log.error("Error subiendo archivo: ", e);
            return ResponseEntity.internalServerError().body("Error al subir el archivo.");
        }
    }

    // 🔐 Helper para sacar el ID del usuario del Token JWT
    private Long getAuthenticatedUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new RuntimeException("Usuario no autenticado");
        }
        // El JwtAuthenticationFilter puso el ID (Long) como 'principal'
        return (Long) auth.getPrincipal();
    }
}
package com.quhealthy.social_service.model;

import com.quhealthy.social_service.model.enums.SocialPlatform;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "social_connections", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"platform_user_id", "platform"}) // Evita duplicar la misma página de FB
})
public class SocialConnection {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private Long providerId; // El ID del Doctor (User Owner)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SocialPlatform platform;

    @Column(name = "platform_user_id", nullable = false)
    private String platformUserId; // ID externo (ej: ID de la FanPage, ID de Instagram Business)

    @Column(nullable = false)
    private String platformUserName; // Nombre legible (ej: "Clínica Dr. House")

    // --- SEGURIDAD (TOKENS) ---
    // Usamos columnDefinition = "TEXT" porque los tokens JWT son enormes
    @Column(columnDefinition = "TEXT", nullable = false)
    private String accessToken;

    @Column(columnDefinition = "TEXT")
    private String refreshToken; // Para renovar acceso sin pedir login de nuevo

    private LocalDateTime tokenExpiresAt;

    // --- METADATA ---
    @Column(columnDefinition = "TEXT")
    private String profileImageUrl; // Foto de perfil de la página/red

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
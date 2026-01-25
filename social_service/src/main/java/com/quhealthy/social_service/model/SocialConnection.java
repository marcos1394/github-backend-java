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
    private String platformUserId; // ID externo (ej: ID de la FanPage)

    @Column(nullable = false)
    private String platformUserName; // Nombre legible (ej: "Clínica Dr. House")

    // --- NUEVO: SOFT DELETE / ESTADO ---
    // Indica si la conexión está vigente. 
    // Si el usuario desvincula, esto pasa a FALSE, pero el registro se queda.
    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    // --- SEGURIDAD (TOKENS) ---
    // ⚠️ CAMBIO: Quitamos 'nullable = false'.
    // Razón: Al hacer Soft Delete, borraremos el token por seguridad (conn.setAccessToken(null)),
    // así que la base de datos debe permitir nulos aquí.
    @Column(columnDefinition = "TEXT") 
    private String accessToken;

    @Column(columnDefinition = "TEXT")
    private String refreshToken;

    private LocalDateTime tokenExpiresAt;

    // --- METADATA ---
    @Column(columnDefinition = "TEXT")
    private String profileImageUrl;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
package com.quhealthy.social_service.model;

import com.quhealthy.social_service.model.enums.PostStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "scheduled_posts")
public class ScheduledPost {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private Long providerId;

    // Relación con la conexión: ¿En qué red se va a publicar esto?
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "social_connection_id", nullable = false)
    private SocialConnection socialConnection;

    // --- CONTENIDO ---
    @Column(columnDefinition = "TEXT", nullable = false)
    private String content; // El "Caption" o texto del post

    // Lista de URLs de imágenes/videos (Almacenados en Google Cloud Storage)
    @ElementCollection
    @CollectionTable(name = "post_media_urls", joinColumns = @JoinColumn(name = "post_id"))
    @Column(name = "media_url")
    private List<String> mediaUrls;

    // --- SCHEDULING ---
    @Column(nullable = false)
    private LocalDateTime scheduledAt; // Cuándo debe salir

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PostStatus status;

    // --- RESULTADO ---
    private String platformPostId; // ID que nos devuelve FB/IG al publicar

    @Column(columnDefinition = "TEXT")
    private String errorMessage; // Si falla, ¿por qué fue?

    // --- GENERACIÓN IA ---
    private boolean generatedByAi; // Flag para saber si lo hizo Gemini
    
    @Column(columnDefinition = "TEXT")
    private String aiPromptUsed; // Guardamos el prompt por si queremos re-generar o auditar

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
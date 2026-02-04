package com.quhealthy.notification_service.model;

import com.quhealthy.notification_service.model.enums.NotificationType;
import com.quhealthy.notification_service.model.enums.TargetRole;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "notifications", indexes = {
        // Índice compuesto vital para: "Dame las notificaciones del Médico X ordenadas por fecha"
        @Index(name = "idx_notif_user_role", columnList = "user_id, target_role, created_at DESC"),
        @Index(name = "idx_notif_read", columnList = "is_read")
})
@EntityListeners(AuditingEntityListener.class)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    // ✅ VITAL: Separa si la notificación es para su perfil de Médico o de Paciente
    @Enumerated(EnumType.STRING)
    @Column(name = "target_role", nullable = false)
    private TargetRole targetRole;

    // ✅ UI: Define el color/ícono (INFO, SUCCESS, WARNING, etc)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Builder.Default
    @Column(name = "is_read", nullable = false)
    private boolean isRead = false;

    // Para Geo: Aquí iría "quhealthy://map?providerId=50"
    @Column(name = "action_link")
    private String actionLink;

    // ✅ GEO FLEXIBLE: Aquí guardamos las coordenadas si es una notificación de ubicación
    // Ej: {"latitude": 19.43, "longitude": -99.13, "promoCode": "DISCOUNT10"}
    @Column(columnDefinition = "TEXT")
    private String metadata;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
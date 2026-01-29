package com.quhealthy.notification_service.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.quhealthy.notification_service.model.enums.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponse {

    private Long id;
    
    private String title;
    
    private String message;
    
    private NotificationType type; // INFO, WARNING, ERROR, SUCCESS
    
    private boolean isRead;
    
    private String actionLink; // La URL a donde redirige al hacer click
    
    // Formato amigable para el frontend (ISO-8601)
    // El frontend se encargará de convertirlo a "Hace 5 minutos"
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;
}
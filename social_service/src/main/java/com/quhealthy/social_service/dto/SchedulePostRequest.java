package com.quhealthy.social_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class SchedulePostRequest {

    @NotNull(message = "Debes seleccionar una conexión social")
    private UUID socialConnectionId; // ¿A qué cuenta va? (Facebook, Instagram...)

    @NotBlank(message = "El contenido del post no puede estar vacío")
    private String content;

    // Opcional: Lista de URLs de imágenes (ya subidas a Cloud Storage)
    private List<String> mediaUrls;

    @NotNull(message = "La fecha de publicación es obligatoria")
    private LocalDateTime scheduledAt;
}
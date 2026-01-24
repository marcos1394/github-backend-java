package com.quhealthy.social_service.dto.ai;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AiVideoRequest {
    
    @NotBlank(message = "El prompt del video es obligatorio")
    private String prompt; // Ej: "Un león majestuoso en la sabana..."

    // Opcional: URL de una imagen base (Image-to-Video)
    // El frontend primero sube la imagen a /upload, recibe la URL y la manda aquí.
    private String imageUrl; 

    private VideoAspectRatio aspectRatio; // Default 16:9

    private boolean hdResolution; // true = 1080p, false = 720p (Default)
}
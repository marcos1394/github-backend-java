package com.quhealthy.social_service.dto.ai;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AiImageRequest {
    
    // SessionId opcional para "edición conversacional"
    private String sessionId;

    @NotBlank(message = "La descripción de la imagen es obligatoria")
    private String prompt; // Ej: "Un doctor sonriendo en un consultorio moderno"

    // Opcional, por defecto Gemini decide.
    private AspectRatio aspectRatio; 
}
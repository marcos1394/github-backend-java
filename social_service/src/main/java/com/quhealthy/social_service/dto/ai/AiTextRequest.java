package com.quhealthy.social_service.dto.ai;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AiTextRequest {
    
    // Si viene vacío, es una conversación nueva.
    // Si trae un UUID, es una continuación (edición).
    private String sessionId; 

    @NotBlank(message = "El prompt no puede estar vacío")
    private String prompt; // Ej: "Escribe un post sobre hidratación"

    private String tone; // Ej: "Profesional", "Divertido", "Urgente"
}
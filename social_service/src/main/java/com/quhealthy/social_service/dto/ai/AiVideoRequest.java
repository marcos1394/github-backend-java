package com.quhealthy.social_service.dto.ai;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AiVideoRequest {

    // Vital para mantener el hilo de la conversación/creación
    private String sessionId; 
    
    @NotBlank(message = "El prompt del video es obligatorio")
    private String prompt; 

    // Opcional: URL de una imagen base (Image-to-Video con Veo)
    private String imageUrl; 

    // Usamos el mismo Enum que en AiImageRequest para consistencia
    private AspectRatio aspectRatio; // Default será 16:9 si viene null

    // Cambiado de boolean a String para soportar "720p", "1080p", "4k"
    private String resolution; 
}
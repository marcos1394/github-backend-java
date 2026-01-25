package com.quhealthy.social_service.dto.ai;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AiVideoResponse {
    private String sessionId; // El ID de rastreo
    private String videoUrl;  // La URL pública en tu bucket
    private String resolution; // Ej: "720p" o "4k"
    private String duration;   // Ej: "8s" (Veo genera clips cortos)
}
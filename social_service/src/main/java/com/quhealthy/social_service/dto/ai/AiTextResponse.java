package com.quhealthy.social_service.dto.ai;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AiTextResponse {
    private String sessionId; // Devolvemos el ID para que el front lo use en la siguiente réplica
    private String generatedText;
}
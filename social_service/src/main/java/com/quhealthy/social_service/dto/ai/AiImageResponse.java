package com.quhealthy.social_service.dto.ai;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AiImageResponse {
    private String sessionId;
    private String imageUrl; // URL pública de Google Cloud Storage
}
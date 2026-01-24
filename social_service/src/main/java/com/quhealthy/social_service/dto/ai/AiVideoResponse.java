package com.quhealthy.social_service.dto.ai;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AiVideoResponse {
    private String videoUrl; // URL pública del video generado
    private String resolution;
    private String duration;
}
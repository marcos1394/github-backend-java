package com.quhealthy.social_service.dto;

import com.quhealthy.social_service.model.enums.SocialPlatform;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class SocialConnectionResponse {
    private UUID id;
    private SocialPlatform platform;
    private String platformUserName; // Ej: "Clínica Dr. House"
    private String profileImageUrl;
    private boolean isConnected; // Para pintar el botón verde en el front
    private LocalDateTime connectedAt;
}
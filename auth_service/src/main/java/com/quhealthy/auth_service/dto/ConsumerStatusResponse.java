package com.quhealthy.auth_service.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ConsumerStatusResponse {
    private Long id;
    private String name;
    private String email;
    private String role; // "CONSUMER"
    private String profileImageUrl;
    private String preferredLanguage;
    private boolean emailVerified;
    private boolean phoneVerified;
    // Aquí podrías agregar en el futuro: "activeSubscriptions", "appointments", etc.
}
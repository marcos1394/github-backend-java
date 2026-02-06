package com.quhealthy.onboarding_service.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProfileResponse {

    private Long providerId;
    private String businessName;
    private String bio;
    private String profileImageUrl;
    private String slug; // URL amigable

    // Ubicación
    private String address;
    private Double latitude;
    private Double longitude;
    private String googlePlaceId;

    // Contacto
    private String websiteUrl;
    private String contactPhone;

    // Categoría
    private Long categoryId;
    private Long subCategoryId;

    // Estado del Onboarding (Para saber si mostrar check verde)
    private String profileStatus;
}
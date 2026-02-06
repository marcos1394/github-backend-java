package com.quhealthy.onboarding_service.dto.response;

import lombok.Builder;
import lombok.Data;
import java.util.Map;

@Data
@Builder
public class OnboardingStatusResponse {

    private Long providerId;

    // Estados de cada paso (PENDING, COMPLETED, REJECTED...)
    private String profileStatus;
    private String kycStatus;
    private String fiscalStatus;     // Futuro
    private String marketplaceStatus;// Futuro
    private String licenseStatus;    // Futuro

    // Si hubo rechazo, aquí decimos por qué (Mapa: "KYC" -> "Foto borrosa")
    private Map<String, String> rejectionReasons;

    // Porcentaje de avance (para barra de progreso en UI)
    private int completionPercentage;
}
package com.quhealthy.onboarding_service.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LicenseResponse {
    private String licenseNumber;    // Número extraído
    private String careerName;       // Profesión
    private String institutionName;  // Universidad
    private String status;           // APPROVED / REJECTED
    private String rejectionReason;  // Texto de error si aplica
    private String documentUrl;      // URL para ver la imagen subida
}
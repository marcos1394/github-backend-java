package com.quhealthy.onboarding_service.dto;

import com.quhealthy.onboarding_service.model.enums.Archetype;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateProfileRequest {

    @NotBlank(message = "El nombre del negocio o consultorio es obligatorio")
    private String businessName;

    @NotBlank(message = "El teléfono de contacto es obligatorio")
    private String phone;

    @NotNull(message = "El arquetipo de negocio es obligatorio")
    private Archetype archetype;

    @NotNull(message = "La categoría principal es obligatoria (Salud/Belleza)")
    private Long parentCategoryId; // 1 = Salud, 2 = Belleza

    private Long subCategoryId; // Opcional en este paso
}
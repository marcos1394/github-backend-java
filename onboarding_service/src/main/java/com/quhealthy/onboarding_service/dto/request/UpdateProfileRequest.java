package com.quhealthy.onboarding_service.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class UpdateProfileRequest {

    // --- IDENTIDAD BÁSICA ---

    @NotBlank(message = "El nombre del consultorio o negocio es obligatorio")
    @Size(min = 3, max = 200, message = "El nombre debe tener entre 3 y 200 caracteres")
    private String businessName;

    @NotBlank(message = "La biografía profesional es obligatoria")
    @Size(min = 20, max = 1000, message = "La biografía debe tener entre 20 y 1000 caracteres")
    private String bio;

    /**
     * URL de la foto. Puede venir de un upload propio o de una referencia de Google Photos.
     */
    @NotBlank(message = "La imagen de perfil es obligatoria")
    private String profileImageUrl;

    // --- UBICACIÓN & GOOGLE MAPS ---

    @NotBlank(message = "La dirección física es obligatoria")
    @Size(max = 400, message = "La dirección no puede exceder 400 caracteres")
    private String address;

    @NotNull(message = "La latitud es obligatoria")
    @DecimalMin(value = "-90.0", message = "Latitud inválida")
    @DecimalMax(value = "90.0", message = "Latitud inválida")
    private Double latitude;

    @NotNull(message = "La longitud es obligatoria")
    @DecimalMin(value = "-180.0", message = "Longitud inválida")
    @DecimalMax(value = "180.0", message = "Longitud inválida")
    private Double longitude;

    /**
     * ID único de Google Places.
     * Vital para vincular reseñas, fotos y actualizaciones futuras.
     */
    @Size(max = 255, message = "El Place ID es demasiado largo")
    private String placeId;

    // --- CONTACTO PÚBLICO (Extraído de Google o manual) ---

    @Size(max = 255, message = "La URL del sitio web es demasiado larga")
    private String websiteUrl;

    @Size(max = 50, message = "El teléfono de contacto es demasiado largo")
    private String contactPhone;

    // --- CATEGORIZACIÓN ---

    @NotNull(message = "La categoría principal es obligatoria")
    private Long categoryId;

    @NotNull(message = "La subcategoría es obligatoria")
    private Long subCategoryId;
}
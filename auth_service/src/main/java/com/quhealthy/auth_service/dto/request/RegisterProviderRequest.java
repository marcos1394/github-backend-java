package com.quhealthy.auth_service.dto.request;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para el registro de nuevos Proveedores.
 *
 * FLUJO DE REGISTRO (Low Friction):
 * 1. El usuario ingresa datos básicos e indica su Industria (ParentCategory: Salud o Belleza).
 * 2. NO seleccionan especialidad específica aquí (eso se hace después en el Onboarding).
 * 3. Se capturan datos de marketing para el bus de eventos.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterProviderRequest {

    // ========================================================================
    // 👤 DATOS DEL TITULAR (Persona Física / Administrador)
    // ========================================================================

    @NotBlank(message = "El nombre del titular es requerido")
    @Size(min = 2, max = 50, message = "El nombre debe tener entre 2 y 50 caracteres")
    private String firstName;

    @NotBlank(message = "El apellido del titular es requerido")
    @Size(min = 2, max = 50, message = "El apellido debe tener entre 2 y 50 caracteres")
    private String lastName;

    // ========================================================================
    // 🏢 DATOS DEL NEGOCIO (Identidad Pública)
    // ========================================================================

    @NotBlank(message = "El nombre del negocio o clínica es requerido")
    @Size(min = 3, max = 100, message = "El nombre del negocio debe tener entre 3 y 100 caracteres")
    private String businessName;

    // ========================================================================
    // 🩺 CATEGORIZACIÓN (NIVEL 1 - INDUSTRIA)
    // ========================================================================

    /**
     * ID de la Categoría Padre (Industria).
     * 1 = Salud, 2 = Belleza, etc.
     * * NOTA: Solo pedimos esto en el registro.
     * La especialidad exacta (Dentista, Spa, etc.) se define en el Onboarding.
     */
    @NotNull(message = "La industria (categoría principal) es requerida")
    @Min(value = 1, message = "ID de categoría inválido")
    private Long parentCategoryId;

    // ========================================================================
    // 📞 CONTACTO Y SEGURIDAD
    // ========================================================================

    @NotBlank(message = "El email es requerido")
    @Email(message = "El formato del correo electrónico no es válido")
    @Size(max = 100, message = "El email no debe exceder los 100 caracteres")
    private String email;

    /**
     * Teléfono móvil para contacto y verificación.
     * RANGO: 10 a 15 caracteres.
     */
    @NotBlank(message = "El teléfono celular es requerido")
    @Size(min = 10, max = 15, message = "El teléfono debe tener entre 10 y 15 caracteres")
    private String phone;

    /**
     * Contraseña segura.
     * Requisitos:
     * - Mínimo 8 caracteres.
     * - Al menos una letra mayúscula.
     * - Al menos una letra minúscula.
     * - Al menos un número.
     */
    @NotBlank(message = "La contraseña es requerida")
    @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres")
    @Pattern(
            regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z]).{8,}$",
            message = "La contraseña debe contener al menos una mayúscula, una minúscula y un número"
    )
    private String password;

    // ========================================================================
    // ⚖️ LEGAL
    // ========================================================================

    /**
     * Validación obligatoria de Términos y Condiciones.
     */
    @AssertTrue(message = "Debes aceptar los Términos y Condiciones para continuar")
    private boolean termsAccepted;

    // ========================================================================
    // 🔗 INTEGRACIÓN Y MARKETING (Para Pub/Sub)
    // ========================================================================

    /**
     * Código de invitación/referido.
     * Se pasa al evento para que ReferralService lo procese.
     */
    private String referralCode;

    /**
     * Fuente de tráfico (Analytics).
     * Ej: "linkedin_ads", "medical_conference".
     */
    private String utmSource;

    /**
     * Medio de tráfico (Analytics).
     */
    private String utmMedium;
}
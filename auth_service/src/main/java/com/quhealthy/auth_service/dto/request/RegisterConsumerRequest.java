package com.quhealthy.auth_service.dto.request;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para el registro de Pacientes (Consumers).
 * * Diseño: "Low Friction".
 * Pedimos solo lo esencial para crear la cuenta. Datos adicionales como
 * teléfono, fecha de nacimiento o género se solicitan progresivamente
 * (Progressive Profiling) una vez dentro de la app.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterConsumerRequest {

    // ========================================================================
    // 👤 IDENTIDAD PERSONAL
    // ========================================================================

    @NotBlank(message = "El nombre es requerido")
    @Size(min = 2, max = 50, message = "El nombre debe tener entre 2 y 50 caracteres")
    private String firstName;

    @NotBlank(message = "El apellido es requerido")
    @Size(min = 2, max = 50, message = "El apellido debe tener entre 2 y 50 caracteres")
    private String lastName;

    // ========================================================================
    // 📧 CREDENCIALES Y SEGURIDAD
    // ========================================================================

    @NotBlank(message = "El email es requerido")
    @Email(message = "El formato del correo electrónico no es válido")
    @Size(max = 100, message = "El email no debe exceder los 100 caracteres")
    private String email;

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

    // Nota: El teléfono es opcional en esta etapa para facilitar el alta.
    // Se pedirá después mediante UpdateConsumerProfileRequest.
    // ========================================================================
    // ⚖️ LEGAL (Nuevo)
    // ========================================================================

    /**
     * Validación obligatoria.
     * El backend rechaza la petición si el usuario no aceptó explícitamente los términos.
     */
    @AssertTrue(message = "Debes aceptar los términos y condiciones para continuar")
    private boolean termsAccepted;

    // ========================================================================
    // 🔗 INTEGRACIÓN Y MARKETING (Nuevos - Opcionales)
    // ========================================================================

    /**
     * Código de invitación/referido.
     * No se valida aquí (se hace en referral_service), pero es necesario recibirlo.
     */
    private String referralCode;

    /**
     * Fuente de tráfico (Analytics).
     * Ej: "facebook", "google", "friend".
     */
    private String utmSource;

    /**
     * Medio de tráfico (Analytics).
     * Ej: "cpc", "email".
     */
    private String utmMedium;
}
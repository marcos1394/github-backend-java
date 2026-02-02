package com.quhealthy.auth_service.dto.request;

import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * DTO para actualizar el perfil del Paciente (Consumer).
 *
 * NOTA DE DISEÑO:
 * Todos los campos son opcionales (pueden ser nulos).
 * La lógica del servicio debe ser: "Si el campo no es null, actualízalo. Si es null, ignóralo".
 * Esto permite usar el mismo endpoint para cambiar solo la foto, o solo el teléfono.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateConsumerProfileRequest {

    // ========================================================================
    // 👤 DATOS BÁSICOS (BaseUser)
    // ========================================================================

    @Size(min = 2, max = 50, message = "El nombre debe tener entre 2 y 50 caracteres")
    private String firstName;

    @Size(min = 2, max = 50, message = "El apellido debe tener entre 2 y 50 caracteres")
    private String lastName;

    /**
     * Teléfono móvil.
     * Vital para activar las notificaciones SMS y el 2FA.
     */
    @Size(min = 10, max = 15, message = "El teléfono debe tener entre 10 y 15 caracteres")
    private String phone;

    /**
     * URL de la nueva imagen de perfil.
     * Generalmente, el frontend sube la imagen a un bucket (S3/Firebase) primero,
     * obtiene la URL, y luego envía esa URL aquí.
     */
    private String profileImageUrl;

    // ========================================================================
    // 🧬 PERFIL PERSONAL
    // ========================================================================

    /**
     * Breve descripción o bio.
     */
    @Size(max = 500, message = "La biografía no debe exceder los 500 caracteres")
    private String bio;

    /**
     * Fecha de nacimiento.
     * Validamos que sea una fecha pasada (@Past).
     */
    @Past(message = "La fecha de nacimiento debe ser válida (en el pasado)")
    private LocalDate birthDate;

    /**
     * Género.
     * Recibimos String para evitar errores de deserialización si envían valores inválidos.
     * Valores esperados: MALE, FEMALE, OTHER, PREFER_NOT_TO_SAY.
     */
    @Pattern(regexp = "^(MALE|FEMALE|OTHER|PREFER_NOT_TO_SAY)$", message = "Valor de género inválido")
    private String gender;

    // ========================================================================
    // ⚙️ PREFERENCIAS Y CONFIGURACIÓN
    // ========================================================================

    @Size(min = 2, max = 5, message = "El idioma debe ser un código ISO (ej: es, en)")
    private String preferredLanguage;

    private String timezone;

    // ========================================================================
    // 🔔 NOTIFICACIONES
    // ========================================================================

    private Boolean emailNotificationsEnabled;
    private Boolean smsNotificationsEnabled;
    private Boolean marketingEmailsOptIn;
    private Boolean appointmentRemindersEnabled;
}
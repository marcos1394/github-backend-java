package com.quhealthy.onboarding_service.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * DTO Unificado para eventos de usuario.
 * Debe coincidir en estructura con el UserEvent del Auth Service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true) // ✅ Evita errores si Auth agrega campos nuevos en el futuro
public class UserEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Tipo de evento (ej: USER_REGISTERED, ONBOARDING_STEP_COMPLETED).
     * Vital para el switch-case de los consumidores.
     */
    private String eventType;

    private Long userId;

    private String email;

    private String role; // PROVIDER, CONSUMER, ADMIN

    /**
     * Datos flexibles.
     * Aquí viaja el 'planId', 'trialEndDate', 'verificationToken', etc.
     */
    private Map<String, Object> payload;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime timestamp;
}
package com.quhealthy.notification_service.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * DTO Espejo del evento emitido por Auth-Service.
 * Debe coincidir campo a campo para que Jackson pueda deserializar el JSON.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true) // 🛡️ Si Auth agrega campos nuevos (ej. sourceIp), no fallamos.
public class UserEvent {

    // Identificador único para idempotencia (evitar procesar doble)
    private String eventId;

    // "USER_REGISTERED", "PASSWORD_RESET_REQUESTED", etc.
    private String eventType;

    private Long userId;

    private String email;

    // "PROVIDER" o "CONSUMER"
    private String role;

    // ✅ CORRECCIÓN: Se llama 'payload', no 'metadata'
    // Aquí dentro vendrá: { "name": "Juan Perez", "token": "xyz" }
    private Map<String, Object> payload;

    private LocalDateTime timestamp;
}
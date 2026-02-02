package com.quhealthy.auth_service.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Evento estandarizado para el Bus de Eventos (Pub/Sub).
 *
 * PROPÓSITO:
 * Desacoplar el Auth Service de los consumidores secundarios.
 * Auth solo dice "Pasó esto" y no le importa quién escuche.
 *
 * CONSUMIDORES TÍPICOS:
 * 1. Notification Service: Envía emails/SMS (Welcome, Reset Password).
 * 2. Referral Service: Procesa códigos de invitación.
 * 3. Analytics Service: Registra orígenes de tráfico (UTMs).
 * 4. Audit Service: Guarda logs de seguridad.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * ID único del evento.
     * Útil para "Idempotencia" (evitar procesar el mismo evento dos veces
     * si Pub/Sub lo entrega duplicado).
     */
    @Builder.Default
    private String eventId = UUID.randomUUID().toString();

    /**
     * Tipo de evento (Dominio).
     * Ejemplos:
     * - "USER_REGISTERED"
     * - "EMAIL_VERIFIED"
     * - "PASSWORD_RESET_REQUESTED"
     * - "ACCOUNT_LOCKED"
     */
    private String eventType;

    /**
     * ID del usuario afectado (Subject).
     */
    private Long userId;

    /**
     * Email del usuario.
     * Se incluye aquí para que NotificationService no tenga que
     * consultar la BD de Auth solo para saber a dónde enviar el correo.
     */
    private String email;

    /**
     * Rol del usuario ("PROVIDER" o "CONSUMER").
     * Permite a los consumidores filtrar o personalizar mensajes.
     * Ej: El email de bienvenida es distinto para Médicos que para Pacientes.
     */
    private String role;

    /**
     * Carga útil dinámica (Metadata).
     * Aquí viaja cualquier dato extra necesario para el evento específico.
     *
     * Ejemplos de claves en el mapa:
     * - "verificationToken": "abc-123"
     * - "name": "Juan Perez"
     * - "referralCode": "REF2024"
     * - "utmSource": "facebook"
     * - "ipAddress": "192.168.1.1"
     */
    private Map<String, Object> payload;

    /**
     * Momento exacto en que ocurrió el evento.
     */
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
}
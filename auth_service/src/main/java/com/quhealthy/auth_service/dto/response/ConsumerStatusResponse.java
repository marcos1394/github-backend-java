package com.quhealthy.auth_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * DTO de Estado del Consumidor (Perfil Completo).
 *
 * Se utiliza en el endpoint: GET /api/auth/me
 *
 * PROPÓSITO:
 * Proporcionar al Frontend toda la información necesaria para:
 * 1. Renderizar el Avatar y Nombre en el Navbar.
 * 2. Llenar los formularios de "Mi Perfil".
 * 3. Mostrar el estado de las verificaciones.
 * 4. Mostrar las preferencias de notificaciones actuales.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsumerStatusResponse {

    // ========================================================================
    // 🆔 IDENTIDAD Y ACCESO
    // ========================================================================

    private Long id;
    private String email;
    private String role; // Siempre "CONSUMER"

    // ========================================================================
    // 👤 PERFIL DE USUARIO (Visual)
    // ========================================================================

    private String firstName;
    private String lastName;

    /**
     * Nombre completo concatenado.
     * Útil para mostrar directamente en la UI sin lógica extra.
     * Ej: "María González"
     */
    private String fullName;

    /**
     * URL de la imagen. Si es null, el frontend muestra un placeholder.
     */
    private String profileImageUrl;

    /**
     * Biografía corta.
     */
    private String bio;

    // ========================================================================
    // 🧬 DATOS DEMOGRÁFICOS
    // ========================================================================

    /**
     * Teléfono (puede ser null si no lo ha registrado aún).
     */
    private String phone;

    private LocalDate birthDate;

    /**
     * Género (MALE, FEMALE, etc).
     * Se envía como String para facilitar el manejo en JSON.
     */
    private String gender;

    // ========================================================================
    // ⚙️ PREFERENCIAS REGIONALES
    // ========================================================================

    private String preferredLanguage; // "es", "en"
    private String timezone;          // "America/Mexico_City"

    // ========================================================================
    // 🚦 ESTADO DE LA CUENTA
    // ========================================================================

    private boolean emailVerified;
    private boolean phoneVerified;

    // ========================================================================
    // 🔔 CONFIGURACIÓN DE NOTIFICACIONES (Agrupada)
    // ========================================================================

    private NotificationSettings notifications;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NotificationSettings {
        private boolean emailEnabled;       // Notificaciones generales
        private boolean smsEnabled;         // Alertas urgentes
        private boolean marketingEnabled;   // Newsletter/Promociones
        private boolean remindersEnabled;   // Recordatorios de citas
    }
}
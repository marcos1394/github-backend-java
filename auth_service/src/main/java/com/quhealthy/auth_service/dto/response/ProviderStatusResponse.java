package com.quhealthy.auth_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO de Estado del Proveedor (Perfil de Negocio Completo).
 *
 * Se utiliza en el endpoint: GET /api/auth/me (cuando el rol es PROVIDER)
 *
 * PROPÓSITO:
 * Entregar al Dashboard del Profesional toda la información necesaria para:
 * 1. Gestión de identidad (Negocio vs Persona).
 * 2. Visualización de estado (Onboarding, Pagos, Verificación).
 * 3. Datos de categorización (Qué soy).
 * 4. Datos de ubicación actual.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderStatusResponse {

    // ========================================================================
    // 🆔 IDENTIDAD DE CUENTA
    // ========================================================================

    private Long id;
    private String email;
    private String role; // "PROVIDER"

    // ========================================================================
    // 🏢 IDENTIDAD DE NEGOCIO (Pública)
    // ========================================================================

    /**
     * Nombre de la clínica, consultorio o marca personal.
     * Ej: "Clínica Dental Sonrisas"
     */
    private String businessName;

    /**
     * Slug único para su perfil web.
     * Ej: "clinica-dental-sonrisas" -> quhealthy.com/dr/clinica-dental-sonrisas
     */
    private String slug;

    /**
     * Logo o Foto de perfil.
     */
    private String profileImageUrl;

    /**
     * Biografía o descripción profesional.
     */
    private String bio;

    // ========================================================================
    // 👤 IDENTIDAD DEL TITULAR (Privada/Admin)
    // ========================================================================

    private String firstName;
    private String lastName;
    private String fullName; // Concatenado
    private String phone;    // Teléfono de contacto directo

    // ========================================================================
    // 🩺 CATEGORIZACIÓN (Especialidad)
    // ========================================================================

    /**
     * Información jerárquica de la especialidad.
     * Usamos una clase interna para enviar ID y Nombre, facilitando al frontend
     * mostrar "Cardiólogo > Pediátrico" sin hacer más peticiones.
     */
    private CategoryInfo category;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryInfo {
        private Long parentId;
        private String parentName;      // Ej: "Salud"

        private Long providerId;
        private String providerName;    // Ej: "Cardiólogo"

        private Long subCategoryId;
        private String subCategoryName; // Ej: "Pediátrico" (puede ser null)
    }

    // ========================================================================
    // 🏷️ CARACTERÍSTICAS (Tags)
    // ========================================================================

    /**
     * Lista de etiquetas activas.
     * Ej: ["Bilingüe", "Wifi Gratis", "Telemedicina"]
     */
    private List<TagResponse> activeTags;

    // ========================================================================
    // 📍 UBICACIÓN
    // ========================================================================

    private LocationInfo location;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LocationInfo {
        private String address;
        private Double latitude;
        private Double longitude;
        // Opcional: timezone, ciudad, estado si los guardamos separados
    }

    // ========================================================================
    // 🚦 ESTADO DEL NEGOCIO (Semáforos)
    // ========================================================================

    private BusinessStatus status;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BusinessStatus {
        /**
         * Verificaciones de seguridad.
         */
        private boolean emailVerified;
        private boolean phoneVerified;

        /**
         * Estado del Onboarding.
         * false = Debe ir al Wizard.
         * true = Puede usar el Dashboard.
         */
        private boolean onboardingComplete;

        /**
         * Paso específico del onboarding donde se quedó (si no ha terminado).
         * Ej: "UPLOAD_DOCUMENTS", "STRIPE_SETUP".
         */
        private String currentOnboardingStep;

        /**
         * Estado de la suscripción/pago.
         * false = Bloquear funciones premium o visibilidad.
         */
        private boolean hasActivePlan;

        /**
         * Estado de aprobación manual (KYC).
         * "PENDING", "APPROVED", "REJECTED".
         */
        private String verificationStatus;

        /**
         * Interruptor maestro.
         * Si el médico quiere ocultar su perfil temporalmente ("De vacaciones").
         */
        private boolean isProfileVisible;
    }

    // ========================================================================
    // 📅 AUDITORÍA
    // ========================================================================

    private LocalDateTime memberSince;
}
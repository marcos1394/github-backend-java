package com.quhealthy.auth_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de Respuesta para Autenticación Exitosa.
 *
 * ES AGNÓSTICO:
 * Sirve tanto para PROVIDER como para CONSUMER.
 *
 * FUNCIÓN PRINCIPAL:
 * Entregar el Token JWT y el ESTADO ACTUAL de la cuenta para que el Frontend
 * sepa a qué pantalla redirigir (Dashboard, Verificación, Onboarding, Pagos).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    /**
     * Token JWT (Json Web Token) firmado.
     * La llave de acceso a los recursos protegidos.
     */
    private String token;

    /**
     * Tipo de token según estándar OAuth2/OIDC.
     * Por defecto siempre es "Bearer".
     */
    @Builder.Default
    private String type = "Bearer";

    /**
     * Token de refresco (Opcional/Futuro).
     * Se usa para obtener un nuevo access token sin volver a pedir credenciales.
     */
    private String refreshToken;

    /**
     * Rol del usuario autenticado.
     * VALORES: "PROVIDER", "CONSUMER", "ADMIN".
     *
     * El Frontend usa esto para cargar el layout correcto:
     * - PROVIDER -> Layout con Sidebar de gestión clínica.
     * - CONSUMER -> Layout de búsqueda y perfil de paciente.
     */
    private String role;

    /**
     * Mensaje amigable para feedback visual (Toast).
     * Ej: "Bienvenido de nuevo, Dr. Juan" o "Login exitoso".
     */
    private String message;

    /**
     * Objeto de Estado (Semáforo de Acceso).
     * Contiene las banderas booleanas que definen el flujo post-login.
     */
    private AuthStatus status;

    // ========================================================================
    // 🚦 CLASE INTERNA DE ESTADO
    // ========================================================================

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuthStatus {

        /**
         * ¿El email está verificado?
         * - true: Continúa.
         * - false: Redirigir a pantalla "Por favor verifica tu correo".
         */
        private boolean isEmailVerified;

        /**
         * ¿El teléfono está verificado?
         * - true: Continúa.
         * - false: Redirigir a pantalla "Verificación SMS" (si es requerido por regla de negocio).
         */
        private boolean isPhoneVerified;

        /**
         * ¿Ha completado el registro de información base?
         *
         * - CONSUMER: Generalmente true tras registro.
         * - PROVIDER:
         * - false: Redirigir a Wizard de Onboarding (KYC, Licencia).
         * - true: Ya tiene perfil base, verificar Plan.
         */
        private boolean onboardingComplete;

        /**
         * ¿Tiene permiso comercial para operar?
         *
         * - CONSUMER: Siempre true (no pagan por usar la app).
         * - PROVIDER:
         * - false: El trial expiró o no ha pagado -> Redirigir a Pasarela de Pagos.
         * - true: Acceso total al Dashboard.
         */
        private boolean hasActivePlan;
    }
}
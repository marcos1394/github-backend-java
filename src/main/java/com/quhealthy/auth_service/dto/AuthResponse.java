package com.quhealthy.auth_service.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuthResponse {
    private String token;          // JWT de sesión completa
    private String partialToken;   // JWT temporal para 2FA
    private String message;        // Mensaje (ej: "Login exitoso", "Verifica tu 2FA")
    
    // Podemos anidar el estado o ponerlo plano. 
    // Para ser consistentes con tu Node.js:
    private AuthStatus status;

    @Data
    @Builder
    public static class AuthStatus {
        private boolean twoFactorRequired;
        private boolean hasActivePlan;
        private boolean onboardingComplete;
        private boolean isEmailVerified;
        private boolean isPhoneVerified;
    }
}
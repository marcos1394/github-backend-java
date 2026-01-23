package com.quhealthy.auth_service.service.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URL;

@Slf4j
@Service
public class AppleAuthService {

    // URL pública donde Apple publica sus llaves para verificar firmas
    private static final String APPLE_KEYS_URL = "https://appleid.apple.com/auth/keys";
    
    // Aquí pondrás tu Bundle ID cuando lo tengas (ej: org.quhealthy.backend.service)
    // Por ahora lo dejamos como constante para fácil reemplazo.
    private static final String APPLE_CLIENT_ID = "PLACEHOLDER_CLIENT_ID"; 

    /**
     * Valida el Identity Token recibido desde el frontend.
     * @param identityToken El JWT en String.
     * @return Los datos (claims) del usuario (email, sub).
     */
    public JWTClaimsSet validateToken(String identityToken) {
        try {
            // 1. Configurar el procesador de JWT
            ConfigurableJWTProcessor<SecurityContext> jwtProcessor = new DefaultJWTProcessor<>();
            
            // 2. Configurar la fuente de llaves (Descarga automática desde Apple)
            JWKSource<SecurityContext> keySource = new RemoteJWKSet<>(new URL(APPLE_KEYS_URL));
            
            // 3. Configurar el selector de algoritmos (Apple usa RS256)
            JWSAlgorithm expectedJWSAlg = JWSAlgorithm.RS256;
            JWSKeySelector<SecurityContext> keySelector = new JWSVerificationKeySelector<>(expectedJWSAlg, keySource);
            jwtProcessor.setJWSKeySelector(keySelector);

            // 4. Procesar y Verificar la firma
            JWTClaimsSet claims = jwtProcessor.process(identityToken, null);

            // 5. Validaciones extra de seguridad (Audience e Issuer)
            // Descomentar cuando tengas el CLIENT_ID real
            /*
            if (!claims.getAudience().contains(APPLE_CLIENT_ID)) {
                throw new RuntimeException("El token no pertenece a esta aplicación.");
            }
            if (!claims.getIssuer().equals("https://appleid.apple.com")) {
                throw new RuntimeException("El emisor del token no es Apple.");
            }
            */

            return claims;

        } catch (Exception e) {
            log.error("❌ Error validando token de Apple: {}", e.getMessage());
            throw new RuntimeException("Token de Apple inválido o expirado.");
        }
    }
}
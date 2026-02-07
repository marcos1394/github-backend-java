package com.quhealthy.onboarding_service.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.Key;
import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Value("${application.security.jwt.secret-key}")
    private String secretKey;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String userEmail;

        // 1. Validar cabecera Authorization
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        jwt = authHeader.substring(7);

        try {
            // 2. Parsear el Token y Verificar Firma
            byte[] keyBytes = Decoders.BASE64.decode(secretKey);
            Key key = Keys.hmacShaKeyFor(keyBytes);

            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(jwt)
                    .getBody();

            userEmail = claims.getSubject();

            // 3. Extraer Datos Enriquecidos (El Pasaporte Digital) 🛂

            // A. ID de Usuario
            Long userId = null;
            if (claims.get("id") != null) {
                userId = ((Number) claims.get("id")).longValue();
            }

            // B. Plan ID
            Long planId = 5L;
            if (claims.get("planId") != null) {
                planId = ((Number) claims.get("planId")).longValue();
            }

            // C. Estados de Onboarding y KYC
            String onboardingStatus = claims.get("onboardingStatus", String.class);
            if (onboardingStatus == null) onboardingStatus = "PENDING";

            String kycStatus = claims.get("kycStatus", String.class);
            if (kycStatus == null) kycStatus = "PENDING";

            String role = claims.get("role", String.class);

            // 4. Establecer contexto de seguridad
            if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {

                List<SimpleGrantedAuthority> authorities = (role != null)
                        ? List.of(new SimpleGrantedAuthority("ROLE_" + role))
                        : Collections.emptyList();

                // ✅ USAMOS EL TOKEN PERSONALIZADO
                // Esto permite al OnboardingController saber en qué paso va el usuario
                // sin consultar la BD en cada petición.
                CustomAuthenticationToken authToken = new CustomAuthenticationToken(
                        userId, // Principal
                        null,   // Credentials
                        authorities,
                        planId,
                        onboardingStatus,
                        kycStatus
                );

                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authToken);
                log.debug("✅ Auth Onboarding: {} | Status: {} | KYC: {}", userEmail, onboardingStatus, kycStatus);
            }
        } catch (Exception e) {
            log.error("❌ Error validando JWT en Onboarding Service: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}
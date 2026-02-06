package com.quhealthy.catalog_service.config;

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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
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

            // 3. Extraer ID y ROL
            Long userId = null;
            if (claims.get("id") != null) {
                // Mantenemos este cast seguro, es vital.
                userId = ((Number) claims.get("id")).longValue();
            }

            String role = claims.get("role", String.class);

            // 4. Establecer contexto de seguridad
            if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {

                List<SimpleGrantedAuthority> authorities = (role != null)
                        ? List.of(new SimpleGrantedAuthority("ROLE_" + role))
                        : Collections.emptyList();

                // El principal es el userId, igual que en Onboarding
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        userId,
                        null,
                        authorities
                );

                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authToken);
                log.debug("✅ Usuario autenticado en Catalog Service: {}", userEmail);
            }
        } catch (Exception e) {
            // Logueamos como WARN o ERROR para monitorear intentos fallidos
            log.error("❌ Error validando JWT en Catalog Service: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}
package com.quhealthy.social_service.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders; // 👈 Importante
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
            // 2. Parsear el Token y Verificar Firma (CORREGIDO)
            // ANTES (ERROR): Usabas UTF_8 directo
            // Key key = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
            
            // AHORA (CORRECTO): Decodificamos Base64 igual que el Auth Service
            byte[] keyBytes = Decoders.BASE64.decode(secretKey);
            Key key = Keys.hmacShaKeyFor(keyBytes);
            
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(jwt)
                    .getBody();

            userEmail = claims.getSubject();
            
            // Extraer ID y ROL
            // Nota: El cast a Integer o Long depende de cómo lo guarda JJWT, a veces lo baja como Integer.
            // Usamos Number para ser seguros y castear a Long.
            Long userId = null;
            if (claims.get("id") != null) {
                userId = ((Number) claims.get("id")).longValue();
            }
            
            String role = claims.get("role", String.class);

            if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                
                // 3. Crear el objeto de autenticación
                List<SimpleGrantedAuthority> authorities = (role != null) 
                        ? List.of(new SimpleGrantedAuthority("ROLE_" + role)) 
                        : Collections.emptyList();

                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        userId, // Guardamos el ID en el principal
                        null,
                        authorities
                );

                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                
                // 4. Establecer contexto
                SecurityContextHolder.getContext().setAuthentication(authToken);
                log.debug("✅ Usuario autenticado en Social Service: {}", userEmail);
            }
        } catch (Exception e) {
            log.error("❌ Error validando JWT en Social Service: {}", e.getMessage());
            // Importante: No detenemos el filtro lanzando excepción, dejamos que siga.
            // Si el contexto queda vacío, SecurityConfig lanzará el 403.
        }

        filterChain.doFilter(request, response);
    }
}
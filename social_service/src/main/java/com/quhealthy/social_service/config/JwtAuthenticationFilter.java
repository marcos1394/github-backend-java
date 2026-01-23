package com.quhealthy.social_service.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
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
import java.nio.charset.StandardCharsets;
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
            Key key = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
            
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(jwt)
                    .getBody();

            userEmail = claims.getSubject();
            
            // OJO: Extraemos el ID y el ROL que pusimos en el Auth Service
            // Asegúrate que en Auth Service estés poniendo el claim "id" o "providerId"
            // Si usas el estándar 'sub' como email, el ID suele ir en claims extra.
            // Aquí asumiremos que el 'sub' es el email y buscaremos un claim 'id'.
            // Si tu Auth Service no pone 'id', avísame para ajustar.
            Long userId = claims.get("id", Long.class); 
            String role = claims.get("role", String.class);

            if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                
                // 3. Crear el objeto de autenticación (Sin ir a BD)
                // "Confiamos en el token"
                List<SimpleGrantedAuthority> authorities = (role != null) 
                        ? List.of(new SimpleGrantedAuthority("ROLE_" + role)) 
                        : Collections.emptyList();

                // Usamos un objeto custom o simple UsernamePasswordAuthenticationToken
                // En el 'principal' guardamos el ID del usuario para acceso rápido en los controllers
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        userId, // Principal -> Lo usaremos como ID
                        null,
                        authorities
                );

                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                
                // 4. Establecer contexto de seguridad
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        } catch (Exception e) {
            log.error("❌ Error validando JWT en Social Service: {}", e.getMessage());
            // No lanzamos error aquí, dejamos que SecurityConfig maneje el 403
        }

        filterChain.doFilter(request, response);
    }
}
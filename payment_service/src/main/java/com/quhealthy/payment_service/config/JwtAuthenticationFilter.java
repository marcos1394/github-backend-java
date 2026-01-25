package com.quhealthy.payment_service.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders; // 👈 Importante para compatibilidad con Auth Service
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
        // Si no hay token, dejamos pasar la petición. 
        // SecurityConfig se encargará de rechazarla si la ruta no es pública (como /webhooks).
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        jwt = authHeader.substring(7);

        try {
            // 2. Parsear el Token y Verificar Firma
            // Usamos Decoders.BASE64 para asegurar que usamos la misma codificación que el Auth Service
            byte[] keyBytes = Decoders.BASE64.decode(secretKey);
            Key key = Keys.hmacShaKeyFor(keyBytes);
            
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(jwt)
                    .getBody();

            userEmail = claims.getSubject();
            
            // 3. Extraer ID y ROL de manera segura
            // El ID viene como número en el JSON, Java lo puede interpretar como Integer o Long.
            // Casteamos a Number primero para evitar ClassCastException.
            Long userId = null;
            if (claims.get("id") != null) {
                userId = ((Number) claims.get("id")).longValue();
            }
            
            String role = claims.get("role", String.class);

            // 4. Establecer Autenticación en el Contexto
            if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                
                List<SimpleGrantedAuthority> authorities = (role != null) 
                        ? List.of(new SimpleGrantedAuthority("ROLE_" + role)) 
                        : Collections.emptyList();

                // Creamos el token de Spring Security usando el ID del usuario como 'Principal'
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        userId, // Guardamos el ID (Long) para usarlo fácil en los Controllers
                        null,
                        authorities
                );

                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                
                SecurityContextHolder.getContext().setAuthentication(authToken);
                log.debug("✅ Usuario autenticado en Payment Service: {}", userEmail);
            }
        } catch (Exception e) {
            // Si el token expiró o la firma está mal, logueamos pero no rompemos el filtro.
            // El contexto de seguridad quedará vacío y SecurityConfig devolverá 403 Forbidden.
            log.error("❌ Error validando JWT en Payment Service: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}
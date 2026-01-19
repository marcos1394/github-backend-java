package com.quhealthy.onboarding_service.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtConfig jwtConfig;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String userEmailOrId;

        // 1. Validar Header
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        jwt = authHeader.substring(7);

        // 2. Extraer ID/Email del Token
        try {
            userEmailOrId = jwtConfig.extractUsername(jwt); // Esto devuelve el 'sub' del token

            // 3. Validar Token (Firma y Expiración)
            if (userEmailOrId != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                if (jwtConfig.isTokenValid(jwt)) {
                    
                    // 4. Crear Authentication Token (SIN ir a base de datos)
                    // Usamos una lista vacía de autoridades por ahora, o podrías extraer roles del token si los pusiste ahí.
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userEmailOrId, // Principal (Aquí guardamos el ID o Email)
                            null,
                            Collections.emptyList() // Authorities (Roles)
                    );

                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    
                    // 5. Inyectar en Contexto de Spring Security
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (Exception e) {
            // Token inválido o expirado -> No autenticamos, dejamos pasar para que SecurityFilterChain lance 403
            System.err.println("Error procesando JWT en Onboarding: " + e.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}
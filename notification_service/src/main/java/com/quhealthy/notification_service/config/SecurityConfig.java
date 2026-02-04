package com.quhealthy.notification_service.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // 1. CSRF Desactivado: Arquitectura Stateless/Rest
            .csrf(AbstractHttpConfigurer::disable)
            
            // 2. CORS Activado: Permite que el Frontend se comunique
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            
            // 3. Reglas de Autorización
            .authorizeHttpRequests(auth -> auth
                // 🔓 PUBLICO: Health Checks (Para Cloud Run/K8s)
                .requestMatchers("/actuator/**").permitAll()
                
                // 🔓 PUBLICO: Webhooks de Terceros (Twilio/Resend)
                // Estos servicios nos llaman para confirmar entrega de SMS/Email.
                // No envían JWT, por lo que deben ser públicos (se validan por firma en el controller).
                .requestMatchers("/api/webhooks/**").permitAll()

                // 🔒 PROTEGIDO: Historial de Notificaciones (La Campanita In-App)
                // Solo el usuario dueño del token puede ver sus alertas.
                .requestMatchers("/api/notifications/**").authenticated()
                
                // 🔒 Todo lo demás requiere autenticación por defecto
                .anyRequest().authenticated()
            )
            
            // 4. Gestión de Sesión: Stateless (Sin cookies)
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            
            // 5. Filtro JWT antes del estándar
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Configuración CORS global.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // Dominios permitidos (Frontend Local y Producción)
        configuration.setAllowedOrigins(List.of("http://localhost:3000", "https://quhealthy.org", "https://www.quhealthy.org"));
        
        // Métodos HTTP permitidos
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        
        // Headers permitidos
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With", "Accept", "Origin", "Access-Control-Request-Method", "Access-Control-Request-Headers"));
        
        // Permitir credenciales
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
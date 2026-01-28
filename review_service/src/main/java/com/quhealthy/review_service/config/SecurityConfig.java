package com.quhealthy.review_service.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod; // 👈 Importante para diferenciar GET de POST
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
            // 1. CSRF Desactivado: Vital para APIs REST
            .csrf(AbstractHttpConfigurer::disable)
            
            // 2. CORS Activado: Homologado con el resto del sistema
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            
            // 3. Reglas de Autorización (Adaptadas para Reseñas)
            .authorizeHttpRequests(auth -> auth
                // 🔓 PUBLICO: Health Checks (Actuator)
                .requestMatchers("/actuator/**").permitAll()
                
                // 🔓 PUBLICO: Leer reseñas (GET)
                // Permitimos que cualquier persona vea el perfil y las reseñas del doctor sin loguearse
                .requestMatchers(HttpMethod.GET, "/api/reviews/**").permitAll()
                
                // 🔒 PROTEGIDO: Crear reseñas y Responder (POST, PUT, DELETE)
                // Aquí exigimos que tenga Token (Paciente o Doctor)
                .anyRequest().authenticated()
            )
            
            // 4. Gestión de Sesión: Stateless
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            
            // 5. Filtro JWT antes del estándar
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Configuración CORS global.
     * Mantenemos la misma configuración que en Payment Service para evitar problemas de integración.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // Dominios permitidos (Frontend Local y Producción)
        configuration.setAllowedOrigins(List.of("http://localhost:3000", "https://quhealthy.org")); 
        
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
package com.quhealthy.catalog_service.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod; // 👈 Importante para diferenciar lectura de escritura
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
            
            // 2. CORS Activado: Homologado con el resto del sistema
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            
            // 3. Reglas de Autorización (Adaptadas para Catálogo)
            .authorizeHttpRequests(auth -> auth
                // 🔓 PUBLICO: Health Checks (Actuator)
                .requestMatchers("/actuator/**").permitAll()
                
                // 🔓 PUBLICO: Leer Servicios y Paquetes (GET)
                // Permitimos que cualquiera vea la "Carta de Servicios" del doctor.
                // Si en el futuro quieres que solo pacientes registrados lo vean, quita esta línea.
                .requestMatchers(HttpMethod.GET, "/api/catalog/**").permitAll()
                
                // 🔒 PROTEGIDO: Crear, Editar, Eliminar (POST, PUT, DELETE)
                // Aquí exigimos Token válido (Doctor gestionando su menú)
                .anyRequest().authenticated()
            )
            
            // 4. Gestión de Sesión: Stateless (Sin cookies de sesión)
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            
            // 5. Filtro JWT antes del estándar
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Configuración CORS global.
     * Mantenemos la misma configuración que en Payment y Review Service.
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
package com.quhealthy.auth_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@SuppressWarnings("unused")
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // 1. EL FILTRO DE SEGURIDAD (El Portero)
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Deshabilitar CSRF (No necesario para APIs Stateless con JWT)
            .csrf(AbstractHttpConfigurer::disable)
            
            // Configurar CORS (Para permitir a Next.js conectarse)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            
            // Definir rutas públicas vs privadas
            .authorizeHttpRequests(auth -> auth
                // Permitir todo lo que empiece con /api/auth/ sin login
                .requestMatchers("/api/auth/**").permitAll()
                // Permitir Swagger/OpenAPI si lo agregamos después
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**").permitAll()
                // Todo lo demás requiere autenticación
                .anyRequest().authenticated()
            )
            
            // Indicar que NO guardaremos sesión en memoria (Stateless)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        return http.build();
    }

    // 2. LA HERRAMIENTA DE ENCRIPTACIÓN (BCrypt)
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // 3. GESTOR DE AUTENTICACIÓN (Lo usaremos en el Login más adelante)
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    // 4. CONFIGURACIÓN DE CORS (Crucial para conectar con Frontend)
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // Permitir el origen de tu frontend (ajusta esto a tu dominio real o localhost)
        configuration.setAllowedOrigins(List.of("http://localhost:3000", "https://quhealthy.com", "https://www.quhealthy.com"));
        
        // Permitir métodos HTTP
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        
        // Permitir cabeceras (Authorization, Content-Type)
        configuration.setAllowedHeaders(List.of("*"));
        
        // Permitir credenciales (cookies, etc)
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
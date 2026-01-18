package com.quhealthy.auth_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod; // <--- IMPORTANTE: Necesario para especificar POST/GET
import org.springframework.security.authentication.AuthenticationManager;
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

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // ========================================================================
    // 1. SECURITY FILTER CHAIN (El Portero Principal)
    // ========================================================================
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // A. Deshabilitar CSRF (Obligatorio para APIs REST Stateless)
            .csrf(AbstractHttpConfigurer::disable)
            
            // B. Configurar CORS (Para que Frontend y Postman pasen)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            
            // C. Reglas de Autorización (Orden: De lo ESPECÍFICO a lo GENÉRICO)
            .authorizeHttpRequests(auth -> auth
                // 1. Health Check (Ping)
                .requestMatchers(HttpMethod.GET, "/api/auth/ping").permitAll()
                
                // 2. Registro de Proveedores (POST Explícito para evitar 403)
                .requestMatchers(HttpMethod.POST, "/api/auth/provider/register").permitAll()
                
                // 3. Documentación Swagger / OpenAPI (Acceso libre para devs)
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                
                // 4. WebSockets (Si se usa en el futuro)
                .requestMatchers("/ws/**").permitAll()
                
                // 5. Red de seguridad: Cualquier otra cosa bajo /api/auth/ pública (Login, Verify, etc.)
                .requestMatchers("/api/auth/**").permitAll()
                
                // 6. TODO LO DEMÁS: Requiere Token JWT
                .anyRequest().authenticated()
            )
            
            // D. Gestión de Sesión: STATELESS (No guardar cookies de sesión)
            .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        return http.build();
    }

    // ========================================================================
    // 2. CONFIGURACIÓN DE CORS (Cross-Origin Resource Sharing)
    // ========================================================================
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // Orígenes permitidos (Frontend Local, Producción, y Postman implícitamente)
        configuration.setAllowedOrigins(List.of(
            "http://localhost:3000",       // Desarrollo Local
            "https://quhealthy.com",       // Producción
            "https://www.quhealthy.com"    // Producción con www
        ));
        
        // Métodos HTTP permitidos
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        
        // Cabeceras permitidas (Authorization, Content-Type, etc.)
        configuration.setAllowedHeaders(List.of("*"));
        
        // Exponer cabeceras al frontend (útil si mandas tokens en headers)
        configuration.setExposedHeaders(List.of("Authorization"));
        
        // Permitir credenciales (Cookies, Auth Headers)
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    // ========================================================================
    // 3. BEANS DE UTILIDAD (Encriptación y Auth Manager)
    // ========================================================================
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
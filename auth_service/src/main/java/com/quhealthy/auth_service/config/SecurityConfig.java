package com.quhealthy.auth_service.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationProvider;
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

    private final JwtAuthenticationFilter jwtAuthFilter; // 💉 Inyectamos el Filtro JWT
    private final AuthenticationProvider authenticationProvider; // 💉 Inyectamos el AuthProvider (desde ApplicationConfig)

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
                
                // 2. Registro de Proveedores (POST Explícito)
                .requestMatchers(HttpMethod.POST, "/api/auth/provider/register").permitAll()
                
                // 3. Documentación Swagger / OpenAPI (Acceso libre para devs)
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                
                // 4. WebSockets (Futuro)
                .requestMatchers("/ws/**").permitAll()
                
                // 5. Endpoints Públicos de Auth (Login, Verify, Reset Pwd)
                // Permitimos todo bajo /api/auth/ explícitamente para Login, Verify, etc.
                .requestMatchers("/api/auth/**").permitAll()

                // 6. Endpoint de Error (Para ver mensajes de error claros en vez de 403)
                .requestMatchers("/error").permitAll()
                
                // 7. TODO LO DEMÁS: Requiere Token JWT
                .anyRequest().authenticated()
            )
            
            // D. Gestión de Sesión: STATELESS (No guardar cookies de sesión)
            .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            
            // E. Configurar el Proveedor de Autenticación
            .authenticationProvider(authenticationProvider)
            
            // F. ⚠️ CRÍTICO: Agregar el filtro JWT ANTES del filtro estándar de usuario/password
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // ========================================================================
    // 2. CONFIGURACIÓN DE CORS (Cross-Origin Resource Sharing)
    // ========================================================================
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // Orígenes permitidos (Frontend Local, Producción)
        configuration.setAllowedOrigins(List.of(
            "http://localhost:3000",       // Desarrollo Local
            "https://quhealthy.org",       // Producción
            "https://www.quhealthy.org"    // Producción con www
        ));
        
        // Métodos HTTP permitidos
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        
        // Cabeceras permitidas
        configuration.setAllowedHeaders(List.of("*"));
        
        // Exponer cabeceras al frontend
        configuration.setExposedHeaders(List.of("Authorization"));
        
        // Permitir credenciales
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
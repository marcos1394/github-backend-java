package com.quhealthy.payment_service.config;

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
            // 1. CSRF Desactivado: Vital para APIs REST y Webhooks POST
            .csrf(AbstractHttpConfigurer::disable)
            
            // 2. CORS Activado: Para que tu Frontend pueda hablar con el Backend
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            
            // 3. Reglas de Autorización (El Portero)
            .authorizeHttpRequests(auth -> auth
                // 🔓 PUBLICO: Webhooks de Stripe y MercadoPago
                // Explicación: Stripe envía una firma en el header 'Stripe-Signature', no un Bearer Token.
                // Por eso debemos permitir la entrada (permitAll) y validar la firma dentro del Controlador.
                .requestMatchers("/api/payments/webhooks/**").permitAll()
                
                // 🔓 PUBLICO: Health Checks de Google Cloud (Actuator)
                .requestMatchers("/actuator/**").permitAll()
                
                // 🔒 PROTEGIDO: Todo lo demás (Checkout, Portal, Cambios de Plan)
                // Aquí sí exigimos el JWT del doctor.
                .anyRequest().authenticated()
            )
            
            // 4. Gestión de Sesión: Stateless (No guardar cookies, usar Tokens)
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            
            // 5. Filtro JWT: Se ejecuta antes del filtro de usuario/contraseña
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Configuración CORS global.
     * Define qué dominios pueden hacer peticiones a este servicio.
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
        
        // Permitir credenciales (Cookies/Auth Headers)
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
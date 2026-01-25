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
            .csrf(AbstractHttpConfigurer::disable) // Desactivado porque usamos JWT y no sesiones de navegador
            .cors(cors -> cors.configurationSource(corsConfigurationSource())) // Habilitar CORS para el Frontend
            .authorizeHttpRequests(auth -> auth
                // 🔓 PUBLICO: Webhooks de Stripe y MercadoPago
                // Es vital que esto sea público porque ellos no envían tu JWT, envían su propia firma.
                .requestMatchers("/api/payments/webhooks/**").permitAll()
                
                // 🔓 PUBLICO: Health Checks de Cloud Run
                .requestMatchers("/actuator/**").permitAll()
                
                // 🔒 PROTEGIDO: Todo lo demás (Activar plan manual, ver historial, cancelar)
                // Requiere que el usuario esté logueado en el Frontend.
                .anyRequest().authenticated()
            )
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Configuración CORS global (Idéntica a Social Service).
     * Permite que tu Frontend (React/Next.js) pueda llamar a este microservicio.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // Dominios permitidos (Frontend Local y Producción)
        configuration.setAllowedOrigins(List.of("http://localhost:3000", "https://quhealthy.org")); 
        
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
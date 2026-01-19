package com.quhealthy.auth_service.config;

import com.quhealthy.auth_service.repository.ConsumerRepository; // 👈 NUEVO IMPORT
import com.quhealthy.auth_service.repository.ProviderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@RequiredArgsConstructor
public class ApplicationConfig {

    private final ProviderRepository providerRepository;
    private final ConsumerRepository consumerRepository; // 👈 INYECCIÓN DEL NUEVO REPO

    /**
     * 1. USER DETAILS SERVICE (UNIFICADO)
     * Busca primero en Providers, si no encuentra, busca en Consumers.
     */
    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            // A. Intentar buscar como Provider
            var provider = providerRepository.findByEmail(username);
            if (provider.isPresent()) {
                return provider.get();
            }

            // B. Intentar buscar como Consumer
            var consumer = consumerRepository.findByEmail(username);
            if (consumer.isPresent()) {
                return consumer.get();
            }

            // C. No existe en ninguno
            throw new UsernameNotFoundException("Usuario no encontrado con email: " + username);
        };
    }

    /**
     * 2. AUTHENTICATION PROVIDER
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        // Usamos el constructor corregido que acepta el servicio unificado
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(userDetailsService());
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    /**
     * 3. AUTHENTICATION MANAGER
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * 4. PASSWORD ENCODER
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
package com.quhealthy.auth_service.config;

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

    /**
     * 1. USER DETAILS SERVICE
     */
    @Bean
    public UserDetailsService userDetailsService() {
        return username -> providerRepository.findByEmail(username)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado con email: " + username));
    }

    /**
     * 2. AUTHENTICATION PROVIDER
     * CORRECCIÓN: Usamos el constructor que acepta UserDetailsService
     * (Basado en tu código fuente descompilado)
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        // ✅ Pasamos el userDetailsService() DIRECTAMENTE al constructor
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(userDetailsService());
        
        // Ya no llamamos a setUserDetailsService porque ya lo pasamos arriba.
        // Solo configuramos el encoder:
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
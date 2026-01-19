package com.quhealthy.auth_service.config;

import com.quhealthy.auth_service.repository.ConsumerRepository;
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
    private final ConsumerRepository consumerRepository;

    /**
     * 1. USER DETAILS SERVICE (UNIFICADO CON LOGS DE DEPURACIÓN)
     */
    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            System.out.println("🔍 [DEBUG LOGIN] Buscando usuario con email: " + username);

            // A. Intentar buscar como PROVIDER
            var provider = providerRepository.findByEmail(username);
            if (provider.isPresent()) {
                System.out.println("✅ [DEBUG LOGIN] Usuario encontrado en tabla PROVIDERS. ID: " + provider.get().getId());
                System.out.println("🔑 [DEBUG LOGIN] Password Hash en memoria: " + provider.get().getPassword());
                return provider.get();
            } else {
                System.out.println("❌ [DEBUG LOGIN] No encontrado en PROVIDERS. Buscando en Consumers...");
            }

            // B. Intentar buscar como CONSUMER
            var consumer = consumerRepository.findByEmail(username);
            if (consumer.isPresent()) {
                System.out.println("✅ [DEBUG LOGIN] Usuario encontrado en tabla CONSUMERS. ID: " + consumer.get().getId());
                
                // DIAGNÓSTICO CRÍTICO: Verificamos si Java está leyendo la contraseña o si llega nula
                String passwordEnMemoria = consumer.get().getPassword();
                System.out.println("🔑 [DEBUG LOGIN] Password Hash leída de BD: " + passwordEnMemoria);
                
                if (passwordEnMemoria == null || passwordEnMemoria.isEmpty()) {
                    System.err.println("🚨 [ALERTA FATAL] La contraseña llegó NULA o VACÍA. Revisa la entidad BaseUser.");
                }

                return consumer.get();
            }

            // C. No existe en ninguno
            System.out.println("⛔ [DEBUG LOGIN] Usuario no encontrado en ninguna tabla.");
            throw new UsernameNotFoundException("Usuario no encontrado con email: " + username);
        };
    }

    /**
     * 2. AUTHENTICATION PROVIDER
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
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
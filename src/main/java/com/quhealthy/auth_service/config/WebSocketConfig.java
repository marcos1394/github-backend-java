package com.quhealthy.auth_service.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 1. Punto de conexión (El "Handshake")
        // El frontend se conectará a: ws://localhost:8080/ws
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*") // Permitir conexiones desde Next.js
                .withSockJS(); // Habilita fallback si el navegador no soporta WS nativo
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 2. Prefijos para rutas
        
        // "/app" es para mensajes que el cliente envía al servidor (ej: chat)
        registry.setApplicationDestinationPrefixes("/app");

        // Habilitamos el Broker en memoria
        // "/queue" -> Para mensajes privados (1 a 1, como notificaciones personales)
        // "/topic" -> Para mensajes públicos (difusión a todos)
        registry.enableSimpleBroker("/queue", "/topic");

        // Prefijo mágico para usuarios específicos
        // Spring convierte "/user/{id}/queue/..." automáticamente
        registry.setUserDestinationPrefix("/user");
    }
}
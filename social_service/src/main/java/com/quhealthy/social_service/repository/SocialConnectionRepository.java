package com.quhealthy.social_service.repository;

import com.quhealthy.social_service.model.SocialConnection;
import com.quhealthy.social_service.model.enums.SocialPlatform;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SocialConnectionRepository extends JpaRepository<SocialConnection, UUID> {
    
    // Buscar todas las conexiones de un doctor
    List<SocialConnection> findByProviderId(Long providerId);

    // Buscar si un doctor ya tiene conectada cierta plataforma (ej: "¿Ya conectó Facebook?")
    Optional<SocialConnection> findByProviderIdAndPlatform(Long providerId, SocialPlatform platform);

    // Buscar por ID de plataforma (útil para webhooks o validaciones)
    Optional<SocialConnection> findByPlatformUserId(String platformUserId);
}
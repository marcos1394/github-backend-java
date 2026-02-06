package com.quhealthy.onboarding_service.repository;

import com.quhealthy.onboarding_service.model.ProviderProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProviderProfileRepository extends JpaRepository<ProviderProfile, Long> {

    /**
     * Verifica si un slug (url amigable) ya existe.
     * Importante para evitar duplicados como "consultorio-dr-simi" vs "consultorio-dr-simi-1".
     */
    boolean existsBySlug(String slug);

    /**
     * Busca el perfil usando el slug público.
     * Usado cuando alguien visita: quhealthy.com/dr-house
     */
    Optional<ProviderProfile> findBySlug(String slug);
}
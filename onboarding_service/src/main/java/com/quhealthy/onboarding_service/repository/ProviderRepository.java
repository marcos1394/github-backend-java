package com.quhealthy.onboarding_service.repository;

import com.quhealthy.onboarding_service.model.Provider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProviderRepository extends JpaRepository<Provider, Long> {
    
    // Método necesario para traducir el Email del Token al ID del Proveedor
    Optional<Provider> findByEmail(String email);
}
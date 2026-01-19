package com.quhealthy.onboarding_service.repository;

import com.quhealthy.onboarding_service.model.Provider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProviderRepository extends JpaRepository<Provider, Long> {
    // Necesario para buscar al proveedor antes de asignarle tags
}
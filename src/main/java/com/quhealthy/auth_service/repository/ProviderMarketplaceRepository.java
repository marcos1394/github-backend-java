package com.quhealthy.auth_service.repository;

import com.quhealthy.auth_service.model.ProviderMarketplace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProviderMarketplaceRepository extends JpaRepository<ProviderMarketplace, Long> {

    // Buscar la configuración de tienda de un proveedor
    Optional<ProviderMarketplace> findByProviderId(Long providerId);

    // Buscar por slug (ej: quhealthy.com/tienda/dr-juan-perez)
    Optional<ProviderMarketplace> findByStoreSlug(String storeSlug);

    // Validar si un slug ya está ocupado
    boolean existsByStoreSlug(String storeSlug);
}
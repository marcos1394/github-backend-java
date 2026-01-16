package com.quhealthy.auth_service.repository;

import com.quhealthy.auth_service.model.ProviderLicense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProviderLicenseRepository extends JpaRepository<ProviderLicense, Long> {

    // Buscar licencia por ID del proveedor
    Optional<ProviderLicense> findByProviderId(Long providerId);

    // Verificar si una cédula ya existe en el sistema (Validación crítica)
    boolean existsByLicenseNumber(String licenseNumber);

    // Buscar por número de cédula
    Optional<ProviderLicense> findByLicenseNumber(String licenseNumber);
}
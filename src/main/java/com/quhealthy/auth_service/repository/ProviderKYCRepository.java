package com.quhealthy.auth_service.repository;

import com.quhealthy.auth_service.model.ProviderKYC;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProviderKYCRepository extends JpaRepository<ProviderKYC, Long> {
    
    // Buscar KYC por ID del proveedor
    Optional<ProviderKYC> findByProviderId(Long providerId);

    // Buscar por ID de sesión (útil para webhooks de servicios externos de KYC)
    Optional<ProviderKYC> findByKycSessionId(String kycSessionId);
}
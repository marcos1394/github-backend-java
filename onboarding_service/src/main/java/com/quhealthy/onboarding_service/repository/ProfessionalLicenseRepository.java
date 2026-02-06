package com.quhealthy.onboarding_service.repository;

import com.quhealthy.onboarding_service.model.ProfessionalLicense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProfessionalLicenseRepository extends JpaRepository<ProfessionalLicense, Long> {
    Optional<ProfessionalLicense> findByProviderId(Long providerId);
}
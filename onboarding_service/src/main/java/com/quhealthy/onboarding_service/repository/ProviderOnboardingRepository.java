package com.quhealthy.onboarding_service.repository;

import com.quhealthy.onboarding_service.model.ProviderOnboarding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProviderOnboardingRepository extends JpaRepository<ProviderOnboarding, Long> {
    // No requiere métodos custom. Usaremos findById(userId) para saber el estado de un usuario.
}
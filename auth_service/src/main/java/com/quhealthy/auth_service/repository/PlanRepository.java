package com.quhealthy.auth_service.repository;

import com.quhealthy.auth_service.model.Plan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PlanRepository extends JpaRepository<Plan, Long> {

    // Buscar por nombre (ej: "Basic", "Pro")
    Optional<Plan> findByName(String name);
    
    // Buscar por ID de precio de Stripe (útil para webhooks de pagos)
    Optional<Plan> findByStripePriceId(String stripePriceId);
}
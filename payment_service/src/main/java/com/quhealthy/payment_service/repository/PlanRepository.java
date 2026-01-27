package com.quhealthy.payment_service.repository;

import com.quhealthy.payment_service.model.Plan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PlanRepository extends JpaRepository<Plan, Long> {

    // 🔎 Busca el plan por su ID de MercadoPago (ej: "b1dbf26d...")
    // Esto nos permitirá saber que ese ID corresponde al "Plan Básico" de $450
    Optional<Plan> findByMpPlanId(String mpPlanId);

    // Opcional: Si algún día quieres validar precios de Stripe desde BD
    Optional<Plan> findByStripePriceId(String stripePriceId);
}
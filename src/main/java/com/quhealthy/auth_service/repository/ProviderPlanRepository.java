package com.quhealthy.auth_service.repository;

import com.quhealthy.auth_service.model.ProviderPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProviderPlanRepository extends JpaRepository<ProviderPlan, Long> {

    // Obtener todo el historial de planes de un proveedor
    List<ProviderPlan> findByProviderId(Long providerId);

    // Buscar por ID de suscripción de Stripe (para cancelaciones/renovaciones)
    Optional<ProviderPlan> findByStripeSubscriptionId(String stripeSubscriptionId);

    // --- QUERY AVANZADA ---
    // Devuelve los planes ACTIVOS o en TRIAL que aún no han vencido (endDate > HOY)
    @Query("SELECT pp FROM ProviderPlan pp WHERE pp.provider.id = :providerId " +
           "AND (pp.status = 'ACTIVE' OR pp.status = 'TRIAL') " +
           "AND pp.endDate > CURRENT_TIMESTAMP " +
           "ORDER BY pp.endDate DESC")
    List<ProviderPlan> findActivePlansByProvider(@Param("providerId") Long providerId);
}
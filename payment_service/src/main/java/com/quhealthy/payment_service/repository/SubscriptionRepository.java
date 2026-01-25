package com.quhealthy.payment_service.repository;

import com.quhealthy.payment_service.model.Subscription;
import com.quhealthy.payment_service.model.enums.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    /**
     * Buscar suscripción por el ID externo (Vital para Webhooks).
     * Stripe nos enviará "sub_12345" y necesitamos saber a cuál corresponde en nuestra BD.
     */
    Optional<Subscription> findByExternalSubscriptionId(String externalSubscriptionId);

    /**
     * Obtener todas las suscripciones de un proveedor (Para el Historial de Pagos).
     * Ordenadas por fecha de creación descendente (lo más nuevo primero).
     */
    List<Subscription> findByProviderIdOrderByCreatedAtDesc(Long providerId);

    /**
     * Búsqueda optimizada para verificar acceso.
     * Busca una suscripción que NO esté cancelada ni expirada para este proveedor.
     * Útil para el Guard/Middleware de otros microservicios.
     */
    @Query("SELECT s FROM Subscription s WHERE s.providerId = :providerId AND s.status IN ('ACTIVE', 'TRIALING')")
    Optional<Subscription> findActiveSubscription(@Param("providerId") Long providerId);
    
    /**
     * Buscar suscripción por ID de cliente externo (ej. para saber quién paga cuando llega un evento de 'invoice.payment_succeeded').
     */
    List<Subscription> findByExternalCustomerId(String externalCustomerId);
}
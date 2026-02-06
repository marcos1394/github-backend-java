package com.quhealthy.auth_service.repository;

import com.quhealthy.auth_service.model.Plan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PlanRepository extends JpaRepository<Plan, Long> {

    /**
     * Spring Data JPA ya incluye automáticamente:
     * - findById(Long id)
     * - findAll()
     * - save(Plan plan)
     * * No necesitas escribirlos.
     */

    /**
     * Búsqueda por nombre.
     * Útil para validar o para scripts de inicialización
     * (ej: buscar si existe "Plan Gratuito").
     */
    Optional<Plan> findByName(String name);

    /**
     * Búsqueda para validar acceso al marketplace.
     * Ej: Traer todos los planes que permiten venta pública.
     */
    boolean existsByIdAndQumarketAccessTrue(Long id);
}
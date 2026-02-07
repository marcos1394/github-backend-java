package com.quhealthy.catalog_service.repository;

import com.quhealthy.catalog_service.model.StoreProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StoreProfileRepository extends JpaRepository<StoreProfile, Long> {

    /**
     * Busca el perfil de tienda por el ID del proveedor.
     * Al extender de JpaRepository, findById ya existe, pero
     * a veces es útil tener métodos semánticos o custom queries.
     */

    // Podemos agregar métodos futuros aquí, por ejemplo:
    Page<StoreProfile> findByMarketplaceVisibleTrue(Pageable pageable);
    // Para listar todas las tiendas "Premium" en un directorio.
}
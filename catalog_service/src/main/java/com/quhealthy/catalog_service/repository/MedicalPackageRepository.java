package com.quhealthy.catalog_service.repository;

import com.quhealthy.catalog_service.model.MedicalPackage;
import com.quhealthy.catalog_service.model.enums.ServiceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MedicalPackageRepository extends JpaRepository<MedicalPackage, Long> {

    // 1. Dashboard del Doctor (Paginado)
    Page<MedicalPackage> findByProviderId(Long providerId, Pageable pageable);

    // 2. Perfil Público (Solo Activos) + Optimización de Carga
    // Usamos @EntityGraph para cargar la relación 'services' en la misma consulta
    // y evitar el problema N+1 de Hibernate.
    @EntityGraph(attributePaths = {"services"})
    List<MedicalPackage> findByProviderIdAndStatus(Long providerId, ServiceStatus status);

    // 3. Buscar detalle de un paquete (con sus servicios)
    @Query("SELECT p FROM MedicalPackage p LEFT JOIN FETCH p.services WHERE p.id = :id")
    Optional<MedicalPackage> findByIdWithServices(@Param("id") Long id);
}
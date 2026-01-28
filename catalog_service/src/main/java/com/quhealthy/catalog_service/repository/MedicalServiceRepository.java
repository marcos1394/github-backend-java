package com.quhealthy.catalog_service.repository;

import com.quhealthy.catalog_service.model.MedicalService;
import com.quhealthy.catalog_service.model.enums.ServiceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MedicalServiceRepository extends JpaRepository<MedicalService, Long> {

    // 1. Para el Dashboard del Doctor (Ve todo: Activos, Inactivos, Archivados)
    Page<MedicalService> findByProviderId(Long providerId, Pageable pageable);

    // 2. Para el Perfil Público (Solo ve lo ACTIVE)
    List<MedicalService> findByProviderIdAndStatus(Long providerId, ServiceStatus status);

    // 3. Validación de duplicados (Para no crear dos "Consulta General")
    boolean existsByProviderIdAndNameIgnoreCase(Long providerId, String name);
    
    // 4. Buscar múltiples servicios por ID (Para armar paquetes)
    // Verifica que los servicios pertenezcan al mismo provider para seguridad
    List<MedicalService> findAllByIdInAndProviderId(List<Long> ids, Long providerId);
}
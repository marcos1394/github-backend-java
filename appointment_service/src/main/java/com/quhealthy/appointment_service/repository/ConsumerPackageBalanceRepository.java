package com.quhealthy.appointment_service.repository;

import com.quhealthy.appointment_service.model.ConsumerPackageBalance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConsumerPackageBalanceRepository extends JpaRepository<ConsumerPackageBalance, Long> {

    /**
     * 🔍 BUSCAR CRÉDITOS VÁLIDOS
     * Busca si el paciente tiene un paquete activo para un servicio específico de un doctor específico.
     * Debe tener créditos > 0.
     */
    @Query("""
        SELECT cpb FROM ConsumerPackageBalance cpb 
        WHERE cpb.consumerId = :consumerId 
        AND cpb.providerId = :providerId 
        AND cpb.serviceId = :serviceId 
        AND cpb.remainingCredits > 0
        ORDER BY cpb.expirationDate ASC
    """)
    List<ConsumerPackageBalance> findRedeemablePackages(
            @Param("consumerId") Long consumerId,
            @Param("providerId") Long providerId,
            @Param("serviceId") Long serviceId
    );
    
    // Ver todos los paquetes de un usuario
    List<ConsumerPackageBalance> findByConsumerId(Long consumerId);
}
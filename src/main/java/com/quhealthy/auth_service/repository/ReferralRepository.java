package com.quhealthy.auth_service.repository;

import com.quhealthy.auth_service.model.Referral;
import com.quhealthy.auth_service.model.enums.ReferralStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReferralRepository extends JpaRepository<Referral, Long> {

    // Ver a quiénes ha invitado un proveedor específico
    List<Referral> findByReferrerId(Long referrerId);

    // Buscar por estado (ej: ver todos los pendientes de pago)
    List<Referral> findByStatus(ReferralStatus status);
}
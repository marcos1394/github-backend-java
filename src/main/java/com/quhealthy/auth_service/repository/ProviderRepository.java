package com.quhealthy.auth_service.repository;

import com.quhealthy.auth_service.model.Provider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProviderRepository extends JpaRepository<Provider, Long> {

    boolean existsByEmail(String email);

    Optional<Provider> findByEmail(String email);

    Optional<Provider> findByReferralCode(String referralCode);

    // ✅ NUEVO: Buscar por Token de Verificación
    Optional<Provider> findByEmailVerificationToken(String token);

    @Query("SELECT p FROM Provider p WHERE p.email = :identifier OR p.phone = :identifier")
    Optional<Provider> findByEmailOrPhone(@Param("identifier") String identifier);
}
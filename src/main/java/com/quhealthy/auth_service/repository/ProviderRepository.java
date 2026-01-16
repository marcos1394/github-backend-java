package com.quhealthy.auth_service.repository;

import com.quhealthy.auth_service.model.Provider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProviderRepository extends JpaRepository<Provider, Long> {

    // Validar si ya existe (Usado en el Registro)
    boolean existsByEmail(String email);

    // Buscar por Email exacto (Usado en validaciones)
    Optional<Provider> findByEmail(String email);

    // Buscar por Código de Referido (Usado para saber quién invitó)
    Optional<Provider> findByReferralCode(String referralCode);

    // --- QUERY ESPECIAL PARA LOGIN ---
    // Busca usuario por Email O por Teléfono en una sola consulta
    @Query("SELECT p FROM Provider p WHERE p.email = :identifier OR p.phone = :identifier")
    Optional<Provider> findByEmailOrPhone(@Param("identifier") String identifier);
}
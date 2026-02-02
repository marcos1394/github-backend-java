package com.quhealthy.auth_service.repository;

import com.quhealthy.auth_service.model.Provider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProviderRepository extends JpaRepository<Provider, Long> {

    // ========================================================================
    // 🔍 BÚSQUEDAS PRINCIPALES (Login & Registro)
    // ========================================================================

    /**
     * Busca un proveedor por email.
     * Utilizado en validaciones de registro y auditoría.
     */
    Optional<Provider> findByEmail(String email);

    /**
     * Busca un proveedor ACTIVO por email (Ignora los eliminados).
     * ⚠️ CRÍTICO: Usar este para el LOGIN en AuthenticationService.
     */
    Optional<Provider> findByEmailAndDeletedAtIsNull(String email);

    /**
     * Verifica existencia rápida por email.
     */
    boolean existsByEmail(String email);

    /**
     * Búsqueda flexible para Login (Email o Teléfono).
     * Solo devuelve usuarios que NO han sido eliminados.
     */
    @Query("SELECT p FROM Provider p WHERE (p.email = :identifier OR p.phone = :identifier) AND p.deletedAt IS NULL")
    Optional<Provider> findByEmailOrPhoneActive(@Param("identifier") String identifier);

    // ========================================================================
    // 🛡️ SEGURIDAD Y VERIFICACIÓN (Tokens & Selectors)
    // ========================================================================

    /**
     * Busca por Token de Verificación de Email.
     * Usado en: VerificationService.verifyEmail()
     */
    Optional<Provider> findByEmailVerificationToken(String token);

    /**
     * Busca por SELECTOR de Restablecimiento de Contraseña.
     * 🔐 PATRÓN SELECTOR/VERIFIER:
     * Buscamos por el selector (público en la URL) para encontrar al usuario.
     * Luego el servicio validará el verifier hash.
     */
    Optional<Provider> findByResetSelector(String resetSelector);

    /**
     * Busca por Token de Verificación de Teléfono (SMS/OTP).
     * Usado en: VerificationService.verifyPhone()
     */
    Optional<Provider> findByPhoneVerificationToken(String token);

    // ========================================================================
    // 💼 BÚSQUEDAS DE NEGOCIO (Específicas de Provider)
    // ========================================================================

    /**
     * Busca por Slug (URL amigable).
     * Ej: "clinica-dental-sonrisas"
     * Útil para validar que no se repita al crear el perfil.
     */
    Optional<Provider> findBySlug(String slug);

    /**
     * Verifica si existe un slug.
     */
    boolean existsBySlug(String slug);

    /*
     * NOTA ARQUITECTÓNICA:
     * Se ha eliminado 'findByStripeSubscriptionId'.
     * La gestión de suscripciones pertenece al Payment Service.
     * El Auth Service solo debe conocer el estado 'hasActivePlan' (boolean).
     */
}
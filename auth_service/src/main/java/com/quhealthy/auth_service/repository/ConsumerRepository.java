package com.quhealthy.auth_service.repository;

import com.quhealthy.auth_service.model.Consumer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ConsumerRepository extends JpaRepository<Consumer, Long> {

    // ========================================================================
    // 🔍 BÚSQUEDAS PRINCIPALES (Login & Registro)
    // ========================================================================

    /**
     * Busca un consumidor por email.
     * Utilizado por:
     * - AuthenticationService (Login)
     * - RegistrationService (Validar duplicados)
     * - VerificationService (Recuperación de contraseña inicial)
     */
    Optional<Consumer> findByEmail(String email);

    /**
     * Busca un consumidor ACTIVO por email (Ignora los eliminados).
     * Útil para evitar login de cuentas dadas de baja lógica.
     */
    Optional<Consumer> findByEmailAndDeletedAtIsNull(String email);

    /**
     * Verifica existencia rápida (retorna boolean).
     * Usado en validaciones de registro.
     */
    boolean existsByEmail(String email);

    // ========================================================================
    // 🛡️ SEGURIDAD Y VERIFICACIÓN (Tokens & Selectors)
    // ========================================================================

    /**
     * Busca por Token de Verificación de Email.
     * Usado en: VerificationService.verifyEmail()
     */
    Optional<Consumer> findByEmailVerificationToken(String token);

    /**
     * Busca por SELECTOR de Restablecimiento de Contraseña.
     * 🔐 PATRÓN SELECTOR/VERIFIER:
     * Buscamos por el selector (público en la URL) para encontrar al usuario.
     * Luego el servicio validará el verifier hash.
     */
    Optional<Consumer> findByResetSelector(String resetSelector);

    /**
     * Busca por Token de Verificación de Teléfono (OTP/SMS).
     * Usado en: VerificationService.verifyPhone()
     */
    Optional<Consumer> findByPhoneVerificationToken(String token);

    // ========================================================================
    // 🔎 BÚSQUEDAS AUXILIARES
    // ========================================================================

    /**
     * Busca por número de teléfono.
     * Útil para validaciones de unicidad.
     */
    Optional<Consumer> findByPhone(String phone);
}
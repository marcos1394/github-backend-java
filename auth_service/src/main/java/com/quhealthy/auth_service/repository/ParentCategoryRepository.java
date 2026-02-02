package com.quhealthy.auth_service.repository;

import com.quhealthy.auth_service.model.ParentCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ParentCategoryRepository extends JpaRepository<ParentCategory, Long> {

    // ========================================================================
    // 🌍 BÚSQUEDAS PÚBLICAS (Frontend & Onboarding)
    // ========================================================================

    /**
     * Busca una categoría por su SLUG (URL friendly).
     * Ejemplo: "salud", "belleza".
     * Vital para routing en el frontend y validación de URLs.
     */
    Optional<ParentCategory> findBySlug(String slug);

    /**
     * Obtiene todas las categorías ACTIVAS ordenadas por su orden de visualización.
     * * CORRECCIÓN: Usamos 'OrderByDisplayOrderAsc' para coincidir con tu campo 'displayOrder'.
     * Útil para llenar los Tabs o Dropdowns en la pantalla de Registro.
     */
    List<ParentCategory> findByIsActiveTrueOrderByDisplayOrderAsc();

    // ========================================================================
    // 🛡️ VALIDACIONES INTERNAS (Admin & Integridad)
    // ========================================================================

    /**
     * Valida si existe una categoría por nombre.
     * Útil para evitar duplicados al crear categorías desde el Admin Panel.
     */
    boolean existsByName(String name);

    /**
     * Valida si existe una categoría por slug.
     * Vital para garantizar URLs únicas y prevenir colisiones.
     */
    boolean existsBySlug(String slug);

    /**
     * Busca por nombre exacto.
     * Útil para scripts de importación o validaciones administrativas.
     */
    Optional<ParentCategory> findByName(String name);
}
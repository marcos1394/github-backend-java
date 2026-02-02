package com.quhealthy.auth_service.repository;

import com.quhealthy.auth_service.model.SubCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubCategoryRepository extends JpaRepository<SubCategory, Long> {

    // ========================================================================
    // 🌍 BÚSQUEDAS PÚBLICAS (Frontend & Onboarding)
    // ========================================================================

    /**
     * Busca una subcategoría por su SLUG único.
     * Ejemplo: "pediatrico".
     */
    Optional<SubCategory> findBySlug(String slug);

    /**
     * Obtiene las subcategorías ACTIVAS de una Categoría Intermedia (CategoryProvider).
     * Ordenadas por displayOrder.
     *
     * Útil para el 3er nivel del Dropdown en cascada:
     * 1. Usuario selecciona "Cardiólogo" (categoryId = 10).
     * 2. Frontend llama a este método.
     * 3. Retorna: [Pediátrico, Adultos, Interventor...]
     */
    List<SubCategory> findByCategoryIdAndIsActiveTrueOrderByDisplayOrderAsc(Long categoryId);

    /**
     * Búsqueda anidada por Slug del padre y Slug de la subcategoría.
     *
     * Vital para validar que la subcategoría realmente pertenece a esa categoría.
     * Ejemplo URL: /cardiologo/pediatrico (Válido)
     * Ejemplo URL: /dermatologo/pediatrico (Inválido, devuelve empty)
     */
    Optional<SubCategory> findByCategorySlugAndSlug(String categorySlug, String slug);

    // ========================================================================
    // 🛡️ VALIDACIONES INTERNAS (Admin & Integridad)
    // ========================================================================

    /**
     * Valida unicidad del slug globalmente.
     */
    boolean existsBySlug(String slug);

    /**
     * Valida si existe un nombre DENTRO de la misma categoría padre.
     * (Es aceptable que "General" exista en varias categorías, pero no dos veces en la misma).
     */
    boolean existsByNameAndCategoryId(String name, Long categoryId);

    /**
     * Obtiene todas las subcategorías de un padre (incluso inactivas).
     * Útil para el Panel de Administración.
     */
    List<SubCategory> findByCategoryIdOrderByDisplayOrderAsc(Long categoryId);
}
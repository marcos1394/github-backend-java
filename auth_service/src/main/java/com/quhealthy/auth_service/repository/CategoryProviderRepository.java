package com.quhealthy.auth_service.repository;

import com.quhealthy.auth_service.model.CategoryProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryProviderRepository extends JpaRepository<CategoryProvider, Long> {

    // ========================================================================
    // 🌍 BÚSQUEDAS PÚBLICAS (Frontend & Onboarding)
    // ========================================================================

    /**
     * Busca una especialidad por su SLUG.
     * Ejemplo: "cardiologo", "spa".
     * Vital para URLs amigables: quhealthy.com/search/salud/cardiologo
     */
    Optional<CategoryProvider> findBySlug(String slug);

    /**
     * Obtiene las especialidades ACTIVAS de una Categoría Padre específica.
     * Ordenadas por displayOrder.
     * * Útil para Dropdowns en cascada:
     * 1. Usuario selecciona "Salud" (parentId = 1).
     * 2. Frontend llama a este método con parentId = 1.
     * 3. Retorna: [Cardiólogo, Ginecólogo, Dermatólogo...]
     */
    List<CategoryProvider> findByParentCategoryIdAndIsActiveTrueOrderByDisplayOrderAsc(Long parentCategoryId);

    /**
     * Búsqueda por Slug de la categoría padre y Slug de la hija.
     * Útil para validar rutas anidadas en el frontend.
     * Ejemplo: /salud/cardiologo
     */
    Optional<CategoryProvider> findByParentCategorySlugAndSlug(String parentSlug, String slug);

    // ========================================================================
    // 🛡️ VALIDACIONES INTERNAS (Admin & Integridad)
    // ========================================================================

    /**
     * Valida si existe una especialidad con ese nombre.
     * Evita duplicados (ej: crear "Dentista" si ya existe "Odontólogo" quizás no,
     * pero sí evita crear "Dentista" dos veces).
     */
    boolean existsByName(String name);

    /**
     * Valida unicidad del slug en todo el sistema.
     */
    boolean existsBySlug(String slug);

    /**
     * Obtiene todas las especialidades de un padre (incluso las inactivas).
     * Útil para el Panel de Administración.
     */
    List<CategoryProvider> findByParentCategoryIdOrderByDisplayOrderAsc(Long parentCategoryId);
}
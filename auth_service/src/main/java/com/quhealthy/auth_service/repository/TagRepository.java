package com.quhealthy.auth_service.repository;

import com.quhealthy.auth_service.model.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TagRepository extends JpaRepository<Tag, Long> {

    // ========================================================================
    // 🌍 BÚSQUEDAS PÚBLICAS (Frontend & Filtros)
    // ========================================================================

    /**
     * Busca un tag por su SLUG.
     * Ejemplo: "bilingue", "certificado".
     * Útil para filtrar búsquedas por URL: /search?tag=bilingue
     */
    Optional<Tag> findBySlug(String slug);

    /**
     * Obtiene todos los tags ACTIVOS ordenados alfabéticamente.
     * Útil para mostrar la lista de filtros disponibles en la barra de búsqueda.
     */
    List<Tag> findByIsActiveTrueOrderByNameAsc();

    /**
     * 🚀 QUERY OPTIMIZADA: Obtiene los tags activos de un proveedor específico.
     *
     * Evita cargar toda la entidad Provider y sus relaciones.
     * Hace el JOIN directamente en la tabla intermedia.
     * Útil para mostrar los "badges" en el perfil del doctor o en el Navbar.
     */
    @Query("SELECT t FROM Tag t JOIN t.providers p WHERE p.id = :providerId AND t.isActive = true")
    List<Tag> findActiveTagsByProviderId(@Param("providerId") Long providerId);

    // ========================================================================
    // 🛡️ VALIDACIONES INTERNAS (Admin & Integridad)
    // ========================================================================

    /**
     * Valida si existe un tag por nombre.
     * Evita duplicados al crear desde el Admin Panel.
     */
    boolean existsByName(String name);

    /**
     * Valida unicidad del slug.
     */
    boolean existsBySlug(String slug);

    /**
     * Busca por nombre exacto.
     * Útil para validaciones o importaciones masivas.
     */
    Optional<Tag> findByName(String name);
}
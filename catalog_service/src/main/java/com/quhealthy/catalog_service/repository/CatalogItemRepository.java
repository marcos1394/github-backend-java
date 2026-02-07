package com.quhealthy.catalog_service.repository;

import com.quhealthy.catalog_service.model.CatalogItem;
import com.quhealthy.catalog_service.model.enums.ItemStatus;
import com.quhealthy.catalog_service.model.enums.ItemType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface CatalogItemRepository extends JpaRepository<CatalogItem, Long>, JpaSpecificationExecutor<CatalogItem> {

    // ==========================================
    // 🔍 1. BÚSQUEDAS BÁSICAS Y VALIDACIONES
    // ==========================================

    /**
     * Dashboard del Doctor: Ver todo su inventario (Activos, Pausados, Archivados).
     */
    Page<CatalogItem> findAllByProviderId(Long providerId, Pageable pageable);

    /**
     * Validación: Evitar duplicados de nombre para un mismo doctor (excluyendo archivados).
     */
    boolean existsByProviderIdAndNameAndStatusNot(Long providerId, String name, ItemStatus status);

    /**
     * ✅ CONTEO PARA LÍMITES DE PLAN (NUEVO)
     * Cuenta cuántos items de cierto tipo tiene el doctor, ignorando los archivados.
     * Ejemplo: "Cuántos SERVICIOS activos o pausados tiene el Dr. House".
     */
    long countByProviderIdAndTypeAndStatusNot(Long providerId, ItemType type, ItemStatus status);

    /**
     * Búsqueda por SKU (Código de Inventario).
     * Vital para integraciones con ERPs o lectores de código de barras.
     */
    Optional<CatalogItem> findByProviderIdAndSku(Long providerId, String sku);

    // ==========================================
    // 🛒 2. BÚSQUEDAS DE TIENDA (Públicas)
    // ==========================================

    /**
     * Catálogo Público del Doctor.
     * Solo devuelve ítems con status específico (ej: ACTIVE).
     */
    Page<CatalogItem> findAllByProviderIdAndStatus(Long providerId, ItemStatus status, Pageable pageable);

    /**
     * Filtrado por Pestañas (ej: "Ver solo Servicios" vs "Ver solo Productos").
     */
    Page<CatalogItem> findAllByProviderIdAndTypeAndStatus(Long providerId, ItemType type, ItemStatus status, Pageable pageable);

    /**
     * Filtrado por Categoría (ej: "Salud" vs "Belleza").
     */
    Page<CatalogItem> findAllByProviderIdAndCategoryAndStatus(Long providerId, String category, ItemStatus status, Pageable pageable);

    // ==========================================
    // 🧠 3. BÚSQUEDA INTELIGENTE (Texto + Tags)
    // ==========================================

    /**
     * Barra de Búsqueda Global ("Google-like").
     * Busca coincidencias en:
     * 1. Nombre (ILIKE - Insensitive)
     * 2. Descripción (ILIKE)
     * 3. Tags de Búsqueda (Array overlap)
     */
    @Query(value = """
        SELECT * FROM catalog_items c
        WHERE c.provider_id = :providerId
        AND c.status = 'ACTIVE'
        AND (
            c.name ILIKE CONCAT('%', :keyword, '%')
            OR c.description ILIKE CONCAT('%', :keyword, '%')
            OR :keyword = ANY(c.search_tags)
        )
        """, nativeQuery = true)
    Page<CatalogItem> searchActiveItems(@Param("providerId") Long providerId,
                                        @Param("keyword") String keyword,
                                        Pageable pageable);

    // ==========================================
    // 📍 4. GEOLOCALIZACIÓN (PostGIS Power) 🚀
    // ==========================================

    /**
     * Búsqueda "Cerca de mí" usando PostGIS Nativo.
     * Utiliza el tipo 'geography' para cálculos precisos sobre la curvatura de la Tierra.
     * @param lat Latitud del usuario
     * @param lng Longitud del usuario
     * @param radiusKm Radio de búsqueda en Kilómetros
     * @return Ítems ordenados del más cercano al más lejano.
     */
    @Query(value = """
        SELECT * FROM catalog_items c
        WHERE c.status = 'ACTIVE'
        AND c.latitude IS NOT NULL
        AND c.longitude IS NOT NULL
        AND ST_DWithin(
            ST_SetSRID(ST_MakePoint(c.longitude, c.latitude), 4326)::geography,
            ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography,
            (:radiusKm * 1000)
        )
        ORDER BY ST_Distance(
            ST_SetSRID(ST_MakePoint(c.longitude, c.latitude), 4326)::geography,
            ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography
        ) ASC
        """, nativeQuery = true)
    Page<CatalogItem> findNearbyItems(@Param("lat") Double lat,
                                      @Param("lng") Double lng,
                                      @Param("radiusKm") Double radiusKm,
                                      Pageable pageable);

    // ==========================================
    // 💎 5. FILTROS ENTERPRISE (JSONB + Precio)
    // ==========================================

    /**
     * Búsqueda por Rango de Precios y Rating.
     */
    @Query("SELECT c FROM CatalogItem c WHERE c.providerId = :providerId " +
            "AND c.status = 'ACTIVE' " +
            "AND c.price BETWEEN :minPrice AND :maxPrice " +
            "AND (:minRating IS NULL OR c.averageRating >= :minRating)")
    Page<CatalogItem> findByPriceRangeAndRating(@Param("providerId") Long providerId,
                                                @Param("minPrice") BigDecimal minPrice,
                                                @Param("maxPrice") BigDecimal maxPrice,
                                                @Param("minRating") Double minRating,
                                                Pageable pageable);

    /**
     * Búsqueda Profunda en Metadata (JSONB).
     * Syntax '??' es el operador de existencia de llave en JSONB de Postgres.
     */
    @Query(value = "SELECT * FROM catalog_items WHERE provider_id = :providerId AND metadata ?? :jsonKey", nativeQuery = true)
    List<CatalogItem> findByMetadataKey(@Param("providerId") Long providerId, @Param("jsonKey") String jsonKey);
}
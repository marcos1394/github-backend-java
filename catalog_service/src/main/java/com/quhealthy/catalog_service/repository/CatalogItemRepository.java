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

    Page<CatalogItem> findAllByProviderId(Long providerId, Pageable pageable);

    boolean existsByProviderIdAndNameAndStatusNot(Long providerId, String name, ItemStatus status);

    long countByProviderIdAndTypeAndStatusNot(Long providerId, ItemType type, ItemStatus status);

    Optional<CatalogItem> findByProviderIdAndSku(Long providerId, String sku);

    // ==========================================
    // 🛒 2. BÚSQUEDAS DE TIENDA (Públicas)
    // ==========================================

    Page<CatalogItem> findAllByProviderIdAndStatus(Long providerId, ItemStatus status, Pageable pageable);

    Page<CatalogItem> findAllByProviderIdAndTypeAndStatus(Long providerId, ItemType type, ItemStatus status, Pageable pageable);

    Page<CatalogItem> findAllByProviderIdAndCategoryAndStatus(Long providerId, String category, ItemStatus status, Pageable pageable);

    // ==========================================
    // 🧠 3. BÚSQUEDA INTELIGENTE (Texto + Tags)
    // ==========================================

    @Query(value = """
        SELECT * FROM catalog_items c
        WHERE c.provider_id = :providerId
        AND c.status = 'ACTIVE'
        AND (
            c.name ILIKE CONCAT('%', :keyword, '%')
            OR c.description ILIKE CONCAT('%', :keyword, '%')
            OR :keyword = ANY(c.search_tags)
        )
        """,
            countQuery = """
        SELECT count(*) FROM catalog_items c
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
        """,
            countQuery = """
        SELECT count(*) FROM catalog_items c
        WHERE c.status = 'ACTIVE'
        AND c.latitude IS NOT NULL
        AND c.longitude IS NOT NULL
        AND ST_DWithin(
            ST_SetSRID(ST_MakePoint(c.longitude, c.latitude), 4326)::geography,
            ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography,
            (:radiusKm * 1000)
        )
        """, nativeQuery = true)
    Page<CatalogItem> findNearbyItems(@Param("lat") Double lat,
                                      @Param("lng") Double lng,
                                      @Param("radiusKm") Double radiusKm,
                                      Pageable pageable);

    // ==========================================
    // 💎 5. FILTROS ENTERPRISE
    // ==========================================

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
     * ✅ CORRECCIÓN FINAL: Usamos jsonb_exists para evitar conflicto de parsers en Spring.
     */
    @Query(value = "SELECT * FROM catalog_items WHERE provider_id = :providerId AND jsonb_exists(metadata, :jsonKey)", nativeQuery = true)
    List<CatalogItem> findByMetadataKey(@Param("providerId") Long providerId, @Param("jsonKey") String jsonKey);
}
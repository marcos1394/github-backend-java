package com.quhealthy.catalog_service.service;

import com.quhealthy.catalog_service.dto.CatalogItemRequest;
import com.quhealthy.catalog_service.dto.CatalogItemResponse;
import com.quhealthy.catalog_service.dto.CatalogItemSummary;
import com.quhealthy.catalog_service.model.CatalogItem;
import com.quhealthy.catalog_service.model.enums.ItemStatus;
import com.quhealthy.catalog_service.model.enums.ItemType;
import com.quhealthy.catalog_service.repository.CatalogItemRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CatalogService {

    private final CatalogItemRepository repository;

    // ==========================================
    // 🏭 1. CREACIÓN Y GESTIÓN (Provider)
    // ==========================================

    @Transactional
    public CatalogItemResponse createItem(Long providerId, CatalogItemRequest request) {
        log.info("Creando ítem para provider {}: {}", providerId, request.getName());

        // 1. Validar Duplicados (Regla de Negocio)
        if (repository.existsByProviderIdAndNameAndStatusNot(providerId, request.getName(), ItemStatus.ARCHIVED)) {
            throw new IllegalArgumentException("Ya existe un servicio o producto activo con este nombre.");
        }

        // 2. Construir Entidad Base
        CatalogItem item = CatalogItem.builder()
                .providerId(providerId)
                .type(request.getType())
                .name(request.getName())
                .description(request.getDescription())
                .imageUrl(request.getImageUrl())
                .category(request.getCategory())
                .price(request.getPrice())
                .compareAtPrice(request.getCompareAtPrice())
                .currency(request.getCurrency())
                .taxRate(request.getTaxRate())
                .status(request.getStatus() != null ? request.getStatus() : ItemStatus.ACTIVE)
                // Geolocalización
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .locationName(request.getLocationName())
                // Innovación: AI Tags & Metadata
                .searchTags(request.getSearchTags())
                .metadata(request.getMetadata())
                .build();

        // 3. Mapeo Específico por Tipo
        mapTypeSpecificFields(item, request);

        // 4. Lógica de PAQUETES (Si aplica)
        if (request.getType() == ItemType.PACKAGE && request.getPackageItemIds() != null) {
            linkPackageItems(item, request.getPackageItemIds(), providerId);
        }

        CatalogItem savedItem = repository.save(item);
        return mapToResponse(savedItem, null, null); // Sin coordenadas de usuario al crear
    }

    @Transactional
    public CatalogItemResponse updateItem(Long providerId, Long itemId, CatalogItemRequest request) {
        CatalogItem item = getOwnedItem(providerId, itemId);

        // Actualizar campos básicos
        item.setName(request.getName());
        item.setDescription(request.getDescription());
        item.setPrice(request.getPrice());
        item.setCompareAtPrice(request.getCompareAtPrice());
        item.setImageUrl(request.getImageUrl());
        item.setStatus(request.getStatus());
        item.setSearchTags(request.getSearchTags());
        item.setMetadata(request.getMetadata());

        // Actualizar Geo
        item.setLatitude(request.getLatitude());
        item.setLongitude(request.getLongitude());
        item.setLocationName(request.getLocationName());

        // Actualizar campos específicos
        mapTypeSpecificFields(item, request);

        // Actualizar Paquete (si cambiaron los items)
        if (item.getType() == ItemType.PACKAGE && request.getPackageItemIds() != null) {
            linkPackageItems(item, request.getPackageItemIds(), providerId);
        }

        return mapToResponse(repository.save(item), null, null);
    }

    @Transactional
    public void deleteItem(Long providerId, Long itemId) {
        CatalogItem item = getOwnedItem(providerId, itemId);
        // Soft Delete (Enterprise Standard)
        item.setStatus(ItemStatus.ARCHIVED);
        repository.save(item);
        log.info("Ítem {} archivado por provider {}", itemId, providerId);
    }

    // ==========================================
    // 🔍 2. BÚSQUEDA Y DISCOVERY (Public/Patient)
    // ==========================================

    @Transactional(readOnly = true)
    public CatalogItemResponse getItemDetail(Long itemId, Double userLat, Double userLng) {
        CatalogItem item = repository.findById(itemId)
                .orElseThrow(() -> new EntityNotFoundException("Ítem no encontrado"));

        // Validar que no esté archivado (salvo que sea el dueño, pero aquí asumimos vista pública)
        if (item.getStatus() == ItemStatus.ARCHIVED) {
            throw new EntityNotFoundException("Este ítem ya no está disponible");
        }

        return mapToResponse(item, userLat, userLng);
    }

    @Transactional(readOnly = true)
    public Page<CatalogItemResponse> getNearbyItems(Double lat, Double lng, Double radiusKm, Pageable pageable) {
        // Usamos el Query Geoespacial de PostGIS
        return repository.findNearbyItems(lat, lng, radiusKm, pageable)
                .map(item -> mapToResponse(item, lat, lng));
    }

    @Transactional(readOnly = true)
    public Page<CatalogItemResponse> searchGlobal(Long providerId, String keyword, Pageable pageable) {
        // Usamos el Query de Texto + Tags
        return repository.searchActiveItems(providerId, keyword, pageable)
                .map(item -> mapToResponse(item, null, null));
    }

    @Transactional(readOnly = true)
    public Page<CatalogItemResponse> getProviderCatalog(Long providerId, String category, Pageable pageable) {
        if (category != null && !category.isEmpty()) {
            return repository.findAllByProviderIdAndCategoryAndStatus(providerId, category, ItemStatus.ACTIVE, pageable)
                    .map(item -> mapToResponse(item, null, null));
        }
        return repository.findAllByProviderIdAndStatus(providerId, ItemStatus.ACTIVE, pageable)
                .map(item -> mapToResponse(item, null, null));
    }

    // ==========================================
    // 🛠️ 3. MÉTODOS PRIVADOS (Helpers)
    // ==========================================

    private void mapTypeSpecificFields(CatalogItem item, CatalogItemRequest request) {
        if (item.getType() == ItemType.SERVICE) {
            item.setDurationMinutes(request.getDurationMinutes());
            item.setModality(request.getModality());
            // Limpiar campos de producto
            item.setStockQuantity(null);
            item.setSku(null);
        } else if (item.getType() == ItemType.PRODUCT) {
            item.setSku(request.getSku());
            item.setStockQuantity(request.getStockQuantity());
            item.setIsDigital(request.getIsDigital());
            // Limpiar campos de servicio
            item.setDurationMinutes(null);
        }
    }

    private void linkPackageItems(CatalogItem packageItem, Set<Long> childIds, Long providerId) {
        List<CatalogItem> children = repository.findAllById(childIds);

        // Validar que todos los items pertenezcan al mismo provider (Seguridad)
        boolean allBelongToProvider = children.stream().allMatch(c -> c.getProviderId().equals(providerId));
        if (!allBelongToProvider) {
            throw new SecurityException("No puedes agregar ítems que no te pertenecen al paquete.");
        }

        packageItem.setPackageItems(new HashSet<>(children));
    }

    private CatalogItem getOwnedItem(Long providerId, Long itemId) {
        CatalogItem item = repository.findById(itemId)
                .orElseThrow(() -> new EntityNotFoundException("Ítem no encontrado"));

        if (!item.getProviderId().equals(providerId)) {
            throw new SecurityException("No tienes permiso para modificar este ítem.");
        }
        return item;
    }

    // --- MAPPER MANUAL (Más rápido y controlado que MapStruct para lógica compleja) ---

    private CatalogItemResponse mapToResponse(CatalogItem item, Double userLat, Double userLng) {

        // 1. Calcular Distancia (si tenemos coordenadas del usuario)
        Double distanceKm = null;
        if (userLat != null && userLng != null && item.getLatitude() != null && item.getLongitude() != null) {
            distanceKm = calculateDistanceKm(userLat, userLng, item.getLatitude(), item.getLongitude());
        }

        // 2. Calcular % Descuento (Marketing)
        Integer discountPct = 0;
        if (item.getCompareAtPrice() != null && item.getCompareAtPrice().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal diff = item.getCompareAtPrice().subtract(item.getPrice());
            // (Diff / CompareAt) * 100
            if (diff.compareTo(BigDecimal.ZERO) > 0) {
                discountPct = diff.divide(item.getCompareAtPrice(), 2, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal(100)).intValue();
            }
        }

        // 3. Mapear Contenido del Paquete (Resumen)
        Set<CatalogItemSummary> packageContents = null;
        if (item.getType() == ItemType.PACKAGE && item.getPackageItems() != null) {
            packageContents = item.getPackageItems().stream()
                    .map(child -> CatalogItemSummary.builder()
                            .id(child.getId())
                            .name(child.getName())
                            .type(child.getType())
                            .imageUrl(child.getImageUrl())
                            .price(child.getPrice())
                            .category(child.getCategory())
                            .build())
                    .collect(Collectors.toSet());
        }

        return CatalogItemResponse.builder()
                .id(item.getId())
                .providerId(item.getProviderId())
                .type(item.getType())
                .name(item.getName())
                .description(item.getDescription())
                .imageUrl(item.getImageUrl())
                .category(item.getCategory())
                .price(item.getPrice())
                .compareAtPrice(item.getCompareAtPrice())
                .currency(item.getCurrency())
                .discountPercentage(discountPct)
                // Geo
                .latitude(item.getLatitude())
                .longitude(item.getLongitude())
                .locationName(item.getLocationName())
                .distanceKm(distanceKm)
                // Específicos
                .durationMinutes(item.getDurationMinutes())
                .modality(item.getModality())
                .sku(item.getSku())
                .stockQuantity(item.getStockQuantity())
                .isDigital(item.getIsDigital())
                // Extras
                .packageContents(packageContents)
                .metadata(item.getMetadata())
                .searchTags(item.getSearchTags())
                .status(item.getStatus())
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .build();
    }

    // Fórmula Haversine simple (Para display en DTO, la búsqueda real la hace PostGIS)
    private Double calculateDistanceKm(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // Radio Tierra km
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        // Redondear a 1 decimal (ej: 5.2 km)
        return BigDecimal.valueOf(R * c).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }
}
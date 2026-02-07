package com.quhealthy.catalog_service.service;

import com.quhealthy.catalog_service.model.enums.ItemStatus;
import com.quhealthy.catalog_service.model.enums.ItemType;
import com.quhealthy.catalog_service.repository.CatalogItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PlanLimitService {

    private final CatalogItemRepository itemRepository;

    /**
     * Valida si el usuario puede crear un ítem según su Plan ID.
     * Basado en los IDs de tu base de datos:
     * 5=Gratis, 1=Básico, 2=Estándar, 3=Premium, 4=Empresarial
     */
    public void validateCreationLimit(Long providerId, Long planId, ItemType type) {

        // ✅ CORRECCIÓN: Contar solo items que NO estén archivados.
        // Si el usuario borró un producto, debe recuperar su cupo.
        long currentCount = itemRepository.countByProviderIdAndTypeAndStatusNot(
                providerId,
                type,
                ItemStatus.ARCHIVED
        );

        // 2. Obtener límite según el plan
        int limit = getLimit(planId, type);

        if (currentCount >= limit) {
            throw new IllegalStateException(
                    "Has alcanzado el límite de " + limit + " " + type + "s activos para tu plan actual. Archiva o elimina ítems para liberar espacio."
            );
        }
    }

    /**
     * Verifica si el plan tiene acceso al Marketplace Global (/nearby).
     */
    public boolean hasMarketplaceAccess(Long planId) {
        // Según tu JSON:
        // Plan 5 (Gratis) -> qumarket_access: false
        // Plan 1 (Básico) -> qumarket_access: false
        // Plan 2, 3, 4    -> qumarket_access: true
        if (planId == null || planId == 5L || planId == 1L) {
            return false;
        }
        return true;
    }

    // Lógica dura de límites (Hardcoded por performance)
    private int getLimit(Long planId, ItemType type) {
        if (planId == null) return 0; // Sin plan no crea nada

        return switch (planId.intValue()) {
            case 5 -> // GRATIS (Trial)
                    (type == ItemType.PACKAGE) ? 0 : 2; // Max 2 prod/serv, 0 paquetes
            case 1 -> // BÁSICO
                    switch (type) {
                        case SERVICE -> 5;
                        case PRODUCT -> 10;
                        case PACKAGE -> 0;
                    };
            case 2 -> // ESTÁNDAR
                    switch (type) {
                        case SERVICE -> 15;
                        case PRODUCT -> 30;
                        case PACKAGE -> 5;
                    };
            case 3 -> // PREMIUM
                    switch (type) {
                        case SERVICE -> 50;
                        case PRODUCT -> 100;
                        case PACKAGE -> 20;
                    };
            case 4 -> 999999; // EMPRESARIAL (Ilimitado)
            default -> 0; // Plan desconocido
        };
    }
}
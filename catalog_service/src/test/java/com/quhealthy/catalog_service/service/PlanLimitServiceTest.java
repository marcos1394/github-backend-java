package com.quhealthy.catalog_service.service;

import com.quhealthy.catalog_service.model.enums.ItemStatus;
import com.quhealthy.catalog_service.model.enums.ItemType;
import com.quhealthy.catalog_service.repository.CatalogItemRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlanLimitServiceTest {

    @Mock
    private CatalogItemRepository itemRepository;

    @InjectMocks
    private PlanLimitService planLimitService;

    // IDs de Planes según tu JSON
    private static final Long PLAN_FREE = 5L;
    private static final Long PLAN_BASIC = 1L;
    private static final Long PLAN_STANDARD = 2L;
    private static final Long PLAN_ENTERPRISE = 4L;

    private static final Long PROVIDER_ID = 100L;

    // ========================================================================
    // 🛡️ TEST: LÍMITES DE CREACIÓN (VALIDATE)
    // ========================================================================

    @Test
    @DisplayName("Plan GRATUITO (5): Debe permitir crear Producto si tiene menos de 2 activos")
    void validate_FreePlan_ShouldAllow_WhenBelowLimit() {
        // GIVEN
        // Simulamos que el usuario tiene 1 producto activo actualmente
        when(itemRepository.countByProviderIdAndTypeAndStatusNot(PROVIDER_ID, ItemType.PRODUCT, ItemStatus.ARCHIVED))
                .thenReturn(1L);

        // WHEN & THEN
        // El límite es 2. Tiene 1. 1 < 2 -> Debe pasar sin excepción.
        assertDoesNotThrow(() ->
                planLimitService.validateCreationLimit(PROVIDER_ID, PLAN_FREE, ItemType.PRODUCT)
        );
    }

    @Test
    @DisplayName("Plan GRATUITO (5): Debe BLOQUEAR si intenta crear el 3er producto")
    void validate_FreePlan_ShouldThrow_WhenLimitReached() {
        // GIVEN
        // Simulamos que ya tiene 2 productos activos
        when(itemRepository.countByProviderIdAndTypeAndStatusNot(PROVIDER_ID, ItemType.PRODUCT, ItemStatus.ARCHIVED))
                .thenReturn(2L);

        // WHEN & THEN
        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                planLimitService.validateCreationLimit(PROVIDER_ID, PLAN_FREE, ItemType.PRODUCT)
        );

        assertTrue(exception.getMessage().contains("límite de 2 PRODUCTs"));
    }

    @Test
    @DisplayName("Plan GRATUITO (5): NO debe permitir crear PAQUETES (Límite 0)")
    void validate_FreePlan_ShouldBlockPackages() {
        // GIVEN
        // Aunque tenga 0 paquetes, el límite es 0.
        when(itemRepository.countByProviderIdAndTypeAndStatusNot(PROVIDER_ID, ItemType.PACKAGE, ItemStatus.ARCHIVED))
                .thenReturn(0L);

        // WHEN & THEN
        assertThrows(IllegalStateException.class, () ->
                planLimitService.validateCreationLimit(PROVIDER_ID, PLAN_FREE, ItemType.PACKAGE)
        );
    }

    @Test
    @DisplayName("Lógica de ARCHIVADO: No debe contar items archivados")
    void validate_ShouldIgnoreArchivedItems() {
        // GIVEN
        // El repositorio devuelve el conteo ignorando ARCHIVED.
        // Aquí verificamos que el servicio llame al método correcto del repositorio.

        planLimitService.validateCreationLimit(PROVIDER_ID, PLAN_BASIC, ItemType.SERVICE);

        // VERIFY: Aseguramos que se llama con StatusNot(ARCHIVED)
        verify(itemRepository).countByProviderIdAndTypeAndStatusNot(
                eq(PROVIDER_ID),
                eq(ItemType.SERVICE),
                eq(ItemStatus.ARCHIVED) // ✅ Esto es lo importante
        );
    }

    @Test
    @DisplayName("Plan EMPRESARIAL (4): Debe permitir virtualmente ilimitados")
    void validate_EnterprisePlan_ShouldAllowManyItems() {
        // GIVEN
        // Usuario tiene 5000 productos
        when(itemRepository.countByProviderIdAndTypeAndStatusNot(anyLong(), any(), any()))
                .thenReturn(5000L);

        // WHEN & THEN
        assertDoesNotThrow(() ->
                planLimitService.validateCreationLimit(PROVIDER_ID, PLAN_ENTERPRISE, ItemType.PRODUCT)
        );
    }

    @Test
    @DisplayName("Debe manejar gracefully si el plan es NULL (bloquear por defecto)")
    void validate_ShouldBlock_WhenPlanIsNull() {
        // GIVEN
        when(itemRepository.countByProviderIdAndTypeAndStatusNot(anyLong(), any(), any()))
                .thenReturn(0L);

        // WHEN & THEN
        // Límite default es 0 si planId es null
        assertThrows(IllegalStateException.class, () ->
                planLimitService.validateCreationLimit(PROVIDER_ID, null, ItemType.SERVICE)
        );
    }

    // ========================================================================
    // 🌍 TEST: ACCESO A MARKETPLACE
    // ========================================================================

    @Test
    @DisplayName("Marketplace: Debe denegar acceso a planes Gratuitos y Básicos")
    void hasMarketplaceAccess_ShouldReturnFalse_ForLowTierPlans() {
        assertFalse(planLimitService.hasMarketplaceAccess(PLAN_FREE), "Plan Gratis no debe tener acceso");
        assertFalse(planLimitService.hasMarketplaceAccess(PLAN_BASIC), "Plan Básico no debe tener acceso");
        assertFalse(planLimitService.hasMarketplaceAccess(null), "Sin plan no debe tener acceso");
    }

    @Test
    @DisplayName("Marketplace: Debe permitir acceso a planes Estándar y superiores")
    void hasMarketplaceAccess_ShouldReturnTrue_ForHighTierPlans() {
        assertTrue(planLimitService.hasMarketplaceAccess(PLAN_STANDARD), "Plan Estándar debe tener acceso");
        assertTrue(planLimitService.hasMarketplaceAccess(3L), "Plan Premium debe tener acceso");
        assertTrue(planLimitService.hasMarketplaceAccess(PLAN_ENTERPRISE), "Plan Enterprise debe tener acceso");
    }
}
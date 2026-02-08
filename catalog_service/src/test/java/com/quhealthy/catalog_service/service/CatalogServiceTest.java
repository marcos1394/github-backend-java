package com.quhealthy.catalog_service.service;

import com.quhealthy.catalog_service.dto.CatalogItemRequest;
import com.quhealthy.catalog_service.dto.CatalogItemResponse;
import com.quhealthy.catalog_service.event.CatalogEventPublisher;
import com.quhealthy.catalog_service.model.CatalogItem;
import com.quhealthy.catalog_service.model.StoreProfile;
import com.quhealthy.catalog_service.model.enums.ItemStatus;
import com.quhealthy.catalog_service.model.enums.ItemType;
import com.quhealthy.catalog_service.repository.CatalogItemRepository;
import com.quhealthy.catalog_service.repository.StoreProfileRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CatalogServiceTest {

    @Mock
    private CatalogItemRepository repository;

    @Mock
    private StoreProfileRepository storeProfileRepository;

    @Mock
    private PlanLimitService planLimitService;

    @Mock
    private CatalogEventPublisher eventPublisher;

    @InjectMocks
    private CatalogService catalogService;

    // Constantes de prueba
    private static final Long PROVIDER_ID = 100L;
    private static final Long PLAN_ID = 2L;
    private static final Long ITEM_ID = 555L;

    // ========================================================================
    // 🏭 TEST: CREACIÓN DE ÍTEMS
    // ========================================================================

    @Test
    @DisplayName("Debe CREAR un ítem exitosamente")
    void createItem_ShouldSucceed_WhenValid() {
        // GIVEN
        CatalogItemRequest request = CatalogItemRequest.builder()
                .name("Consulta General")
                .type(ItemType.SERVICE)
                .price(new BigDecimal("500.00"))
                .description("Consulta médica")
                .category("SALUD")
                .build();

        doNothing().when(planLimitService).validateCreationLimit(PROVIDER_ID, PLAN_ID, ItemType.SERVICE);
        when(repository.existsByProviderIdAndNameAndStatusNot(any(), any(), any())).thenReturn(false);
        when(planLimitService.hasMarketplaceAccess(PLAN_ID)).thenReturn(true);

        CatalogItem savedItem = CatalogItem.builder()
                .id(ITEM_ID)
                .providerId(PROVIDER_ID)
                .name(request.getName())
                .type(request.getType())
                .status(ItemStatus.ACTIVE)
                .price(request.getPrice())
                .category("SALUD")
                .build();
        when(repository.save(any(CatalogItem.class))).thenReturn(savedItem);
        when(storeProfileRepository.existsById(PROVIDER_ID)).thenReturn(false);

        // WHEN
        CatalogItemResponse response = catalogService.createItem(PROVIDER_ID, request, PLAN_ID);

        // THEN
        assertThat(response.getId()).isEqualTo(ITEM_ID);
        verify(eventPublisher).publish(eq(PROVIDER_ID), eq("ITEM_CREATED"), anyMap());
    }

    @Test
    @DisplayName("Debe CREAR un PAQUETE y vincular ítems hijos (Coverage linkPackageItems)")
    void createItem_ShouldLinkPackages_WhenValid() {
        // GIVEN
        Set<Long> childIds = Set.of(1L, 2L);
        CatalogItemRequest request = CatalogItemRequest.builder()
                .name("Paquete Salud")
                .type(ItemType.PACKAGE) // ✅ Tipo Paquete
                .packageItemIds(childIds) // ✅ IDs de hijos
                .price(BigDecimal.TEN)
                .category("PACK")
                .build();

        // Mocks de hijos
        CatalogItem child1 = CatalogItem.builder().id(1L).providerId(PROVIDER_ID).build();
        CatalogItem child2 = CatalogItem.builder().id(2L).providerId(PROVIDER_ID).build();
        when(repository.findAllById(childIds)).thenReturn(List.of(child1, child2));

        // Mocks standard
        doNothing().when(planLimitService).validateCreationLimit(any(), any(), any());
        when(repository.save(any(CatalogItem.class))).thenAnswer(i -> {
            CatalogItem item = (CatalogItem) i.getArguments()[0];
            item.setId(ITEM_ID);
            return item;
        });

        // WHEN
        CatalogItemResponse response = catalogService.createItem(PROVIDER_ID, request, PLAN_ID);

        // THEN
        // Verificamos que se guardó con los hijos
        ArgumentCaptor<CatalogItem> captor = ArgumentCaptor.forClass(CatalogItem.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getPackageItems()).hasSize(2);
    }

    @Test
    @DisplayName("Debe FALLAR al crear paquete con ítems de OTRO provider (Security)")
    void createItem_ShouldThrow_WhenPackageIncludesForeignItem() {
        // GIVEN
        Set<Long> childIds = Set.of(99L);
        CatalogItemRequest request = CatalogItemRequest.builder()
                .type(ItemType.PACKAGE)
                .packageItemIds(childIds)
                .build();

        // Hijo pertenece a provider 999 (ajeno)
        CatalogItem foreignChild = CatalogItem.builder().id(99L).providerId(999L).build();
        when(repository.findAllById(childIds)).thenReturn(List.of(foreignChild));

        doNothing().when(planLimitService).validateCreationLimit(any(), any(), any());

        // WHEN & THEN
        assertThatThrownBy(() -> catalogService.createItem(PROVIDER_ID, request, PLAN_ID))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("Debe ACTUALIZAR el perfil si ya existe pero cambió el permiso (Coverage ensureStoreProfileExists)")
    void createItem_ShouldUpdateProfile_WhenExistsAndPlanChanged() {
        // GIVEN
        CatalogItemRequest request = CatalogItemRequest.builder()
                .name("Item Test") // Nombre en request
                .type(ItemType.PRODUCT)
                .price(BigDecimal.ONE)
                .category("GENERAL")
                .build();

        doNothing().when(planLimitService).validateCreationLimit(any(), any(), any());

        // ✅ CORRECCIÓN: El mock debe devolver un objeto con NOMBRE y CATEGORÍA para que Map.of no falle
        CatalogItem mockSavedItem = CatalogItem.builder()
                .id(1L)
                .name("Item Test") // 👈 ¡ESTO FALTABA!
                .type(ItemType.PRODUCT)
                .category("GENERAL")
                .status(ItemStatus.ACTIVE)
                .build();

        when(repository.save(any())).thenReturn(mockSavedItem);

        // Simulamos que el plan da acceso, pero el perfil guardado tiene acceso FALSE
        when(planLimitService.hasMarketplaceAccess(PLAN_ID)).thenReturn(true);
        when(storeProfileRepository.existsById(PROVIDER_ID)).thenReturn(true); // Ya existe

        StoreProfile existingProfile = StoreProfile.builder().providerId(PROVIDER_ID).marketplaceVisible(false).build();
        when(storeProfileRepository.getReferenceById(PROVIDER_ID)).thenReturn(existingProfile);

        // WHEN
        catalogService.createItem(PROVIDER_ID, request, PLAN_ID);

        // THEN
        // Se debe guardar la actualización del perfil
        verify(storeProfileRepository).save(existingProfile);
        assertThat(existingProfile.isMarketplaceVisible()).isTrue();
    }
    // ========================================================================
    // 🔍 TEST: LECTURA Y CÁLCULOS (Coverage mapToResponse & distance)
    // ========================================================================

    @Test
    @DisplayName("GetItemDetail: Debe calcular DISTANCIA y DESCUENTO (Coverage calculateDistanceKm)")
    void getItemDetail_ShouldCalculateDistanceAndDiscount() {
        // GIVEN
        CatalogItem item = CatalogItem.builder()
                .id(ITEM_ID)
                .providerId(PROVIDER_ID)
                .price(new BigDecimal("100.00"))
                .compareAtPrice(new BigDecimal("150.00")) // Tiene descuento
                .latitude(19.4326) // CDMX
                .longitude(-99.1332)
                .status(ItemStatus.ACTIVE)
                .build();

        when(repository.findById(ITEM_ID)).thenReturn(Optional.of(item));

        // WHEN
        // Usuario a ~1km de distancia (aprox)
        Double userLat = 19.4426;
        Double userLng = -99.1332;
        CatalogItemResponse response = catalogService.getItemDetail(ITEM_ID, userLat, userLng);

        // THEN
        assertThat(response.getDistanceKm()).isNotNull();
        assertThat(response.getDistanceKm()).isGreaterThan(0.0); // Debe haber distancia
        assertThat(response.getDiscountPercentage()).isEqualTo(33); // (50/150)*100 = 33.33% -> 33
    }

    @Test
    @DisplayName("GetNearbyItems: Debe retornar lista paginada")
    void getNearbyItems_ShouldReturnList() {
        // GIVEN
        CatalogItem item = CatalogItem.builder().id(1L).latitude(10.0).longitude(10.0).build();
        Page<CatalogItem> page = new PageImpl<>(List.of(item));
        when(repository.findNearbyItems(anyDouble(), anyDouble(), anyDouble(), any())).thenReturn(page);

        // WHEN
        Page<CatalogItemResponse> result = catalogService.getNearbyItems(10.0, 10.0, 5.0, Pageable.unpaged());

        // THEN
        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    @DisplayName("SearchGlobal: Debe retornar lista")
    void searchGlobal_ShouldReturnList() {
        Page<CatalogItem> page = new PageImpl<>(List.of(new CatalogItem()));
        when(repository.searchActiveItems(any(), anyString(), any())).thenReturn(page);

        Page<CatalogItemResponse> result = catalogService.searchGlobal(PROVIDER_ID, "query", Pageable.unpaged());

        assertThat(result).isNotEmpty();
    }

    @Test
    @DisplayName("GetProviderCatalog: Debe filtrar por categoría si se provee")
    void getProviderCatalog_ShouldFilterByCategory() {
        Page<CatalogItem> page = new PageImpl<>(List.of(new CatalogItem()));
        when(repository.findAllByProviderIdAndCategoryAndStatus(any(), eq("SALUD"), any(), any())).thenReturn(page);

        catalogService.getProviderCatalog(PROVIDER_ID, "SALUD", Pageable.unpaged());

        verify(repository).findAllByProviderIdAndCategoryAndStatus(any(), eq("SALUD"), any(), any());
    }

    @Test
    @DisplayName("GetProviderCatalog: Debe traer todo si no hay categoría")
    void getProviderCatalog_ShouldReturnAll_WhenCategoryNull() {
        Page<CatalogItem> page = new PageImpl<>(List.of(new CatalogItem()));
        when(repository.findAllByProviderIdAndStatus(any(), any(), any())).thenReturn(page);

        catalogService.getProviderCatalog(PROVIDER_ID, null, Pageable.unpaged());

        verify(repository).findAllByProviderIdAndStatus(any(), any(), any());
    }

    @Test
    @DisplayName("GetStoreProfile: Debe retornar perfil")
    void getStoreProfile_ShouldReturnProfile() {
        StoreProfile profile = new StoreProfile();
        when(storeProfileRepository.findById(PROVIDER_ID)).thenReturn(Optional.of(profile));

        StoreProfile result = catalogService.getStoreProfile(PROVIDER_ID);

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("GetStoreProfile: Debe lanzar error si no existe")
    void getStoreProfile_ShouldThrow_WhenNotFound() {
        when(storeProfileRepository.findById(PROVIDER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> catalogService.getStoreProfile(PROVIDER_ID))
                .isInstanceOf(EntityNotFoundException.class);
    }

    // ========================================================================
    // OTROS TESTS EXISTENTES (Mantener create/update/delete básicos)
    // ========================================================================

    @Test
    void deleteItem_ShouldArchiveAndPublishEvent() {
        CatalogItem item = CatalogItem.builder().id(ITEM_ID).providerId(PROVIDER_ID).build();
        when(repository.findById(ITEM_ID)).thenReturn(Optional.of(item));
        catalogService.deleteItem(PROVIDER_ID, ITEM_ID);
        verify(eventPublisher).publish(eq(PROVIDER_ID), eq("ITEM_ARCHIVED"), anyMap());
    }

    @Test
    void updateItem_ShouldThrow_WhenNotOwner() {
        CatalogItem itemDeOtro = CatalogItem.builder().id(ITEM_ID).providerId(999L).build();
        when(repository.findById(ITEM_ID)).thenReturn(Optional.of(itemDeOtro));
        CatalogItemRequest request = CatalogItemRequest.builder().build();
        assertThatThrownBy(() -> catalogService.updateItem(PROVIDER_ID, ITEM_ID, request)).isInstanceOf(SecurityException.class);
    }

    @Test
    void createItem_ShouldThrow_WhenLimitExceeded() {
        CatalogItemRequest request = CatalogItemRequest.builder().type(ItemType.PRODUCT).build();
        doThrow(new IllegalStateException()).when(planLimitService).validateCreationLimit(any(), any(), any());
        assertThatThrownBy(() -> catalogService.createItem(PROVIDER_ID, request, PLAN_ID)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void createItem_ShouldThrow_WhenDuplicateName() {
        CatalogItemRequest request = CatalogItemRequest.builder().name("Dup").type(ItemType.SERVICE).build();
        when(repository.existsByProviderIdAndNameAndStatusNot(any(), any(), any())).thenReturn(true);
        assertThatThrownBy(() -> catalogService.createItem(PROVIDER_ID, request, PLAN_ID)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getItemDetail_ShouldThrow_WhenArchived() {
        CatalogItem item = CatalogItem.builder().id(ITEM_ID).status(ItemStatus.ARCHIVED).build();
        when(repository.findById(ITEM_ID)).thenReturn(Optional.of(item));
        assertThatThrownBy(() -> catalogService.getItemDetail(ITEM_ID, null, null)).isInstanceOf(EntityNotFoundException.class);
    }
}
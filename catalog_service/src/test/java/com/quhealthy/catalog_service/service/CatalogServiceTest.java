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

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

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
    private static final Long PLAN_ID = 2L; // Plan Estándar
    private static final Long ITEM_ID = 555L;

    // ========================================================================
    // 🏭 TEST: CREACIÓN DE ÍTEMS
    // ========================================================================

    @Test
    @DisplayName("Debe CREAR un ítem exitosamente, asegurar StoreProfile y publicar evento")
    void createItem_ShouldSucceed_WhenValid() {
        // GIVEN
        CatalogItemRequest request = CatalogItemRequest.builder()
                .name("Consulta General")
                .type(ItemType.SERVICE)
                .price(new BigDecimal("500.00"))
                .description("Consulta médica")
                .build();

        // Mocks de Validaciones
        doNothing().when(planLimitService).validateCreationLimit(PROVIDER_ID, PLAN_ID, ItemType.SERVICE);
        when(repository.existsByProviderIdAndNameAndStatusNot(any(), any(), any())).thenReturn(false);
        when(planLimitService.hasMarketplaceAccess(PLAN_ID)).thenReturn(true);

        // Mock de Guardado
        CatalogItem savedItem = CatalogItem.builder()
                .id(ITEM_ID)
                .providerId(PROVIDER_ID)
                .name(request.getName())
                .type(request.getType())
                .status(ItemStatus.ACTIVE)
                .price(request.getPrice())
                .build();
        when(repository.save(any(CatalogItem.class))).thenReturn(savedItem);

        // Mock de StoreProfile (No existe, debe crearse)
        when(storeProfileRepository.existsById(PROVIDER_ID)).thenReturn(false);

        // WHEN
        CatalogItemResponse response = catalogService.createItem(PROVIDER_ID, request, PLAN_ID);

        // THEN
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(ITEM_ID);

        // 1. Verificar que se validó el límite
        verify(planLimitService).validateCreationLimit(PROVIDER_ID, PLAN_ID, ItemType.SERVICE);

        // 2. Verificar que se creó el StoreProfile (Identidad)
        verify(storeProfileRepository).save(any(StoreProfile.class));

        // 3. ✅ VERIFICACIÓN CRÍTICA: Evento ITEM_CREATED
        ArgumentCaptor<Map<String, Object>> eventCaptor = ArgumentCaptor.forClass(Map.class);
        verify(eventPublisher).publish(eq(PROVIDER_ID), eq("ITEM_CREATED"), eventCaptor.capture());

        Map<String, Object> payload = eventCaptor.getValue();
        assertThat(payload).containsEntry("itemId", ITEM_ID);
        assertThat(payload).containsEntry("name", "Consulta General");
    }

    @Test
    @DisplayName("Debe FALLAR si se excede el límite del plan")
    void createItem_ShouldThrow_WhenLimitExceeded() {
        // GIVEN
        CatalogItemRequest request = CatalogItemRequest.builder().type(ItemType.PRODUCT).build();

        // Simulamos que el PlanLimitService lanza excepción
        doThrow(new IllegalStateException("Límite alcanzado"))
                .when(planLimitService).validateCreationLimit(PROVIDER_ID, PLAN_ID, ItemType.PRODUCT);

        // WHEN & THEN
        assertThatThrownBy(() -> catalogService.createItem(PROVIDER_ID, request, PLAN_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Límite alcanzado");

        // Asegurar que NADA se guardó ni publicó
        verify(repository, never()).save(any());
        verify(eventPublisher, never()).publish(any(), any(), any());
    }

    @Test
    @DisplayName("Debe FALLAR si el nombre está duplicado")
    void createItem_ShouldThrow_WhenDuplicateName() {
        // GIVEN
        CatalogItemRequest request = CatalogItemRequest.builder().name("Duplicado").type(ItemType.SERVICE).build();

        // Mock validación OK, pero repositorio dice que ya existe
        doNothing().when(planLimitService).validateCreationLimit(any(), any(), any());
        when(repository.existsByProviderIdAndNameAndStatusNot(PROVIDER_ID, "Duplicado", ItemStatus.ARCHIVED))
                .thenReturn(true);

        // WHEN & THEN
        assertThatThrownBy(() -> catalogService.createItem(PROVIDER_ID, request, PLAN_ID))
                .isInstanceOf(IllegalArgumentException.class);

        verify(repository, never()).save(any());
    }

    // ========================================================================
    // ✏️ TEST: ACTUALIZACIÓN
    // ========================================================================

    @Test
    @DisplayName("Debe ACTUALIZAR un ítem y publicar evento")
    void updateItem_ShouldSucceed_WhenOwnerIsCorrect() {
        // GIVEN
        CatalogItemRequest request = CatalogItemRequest.builder()
                .name("Nombre Nuevo")
                .price(new BigDecimal("600.00"))
                .build();

        CatalogItem existingItem = CatalogItem.builder()
                .id(ITEM_ID)
                .providerId(PROVIDER_ID) // Es el dueño correcto
                .name("Viejo")
                .type(ItemType.SERVICE)
                .status(ItemStatus.ACTIVE)
                .price(new BigDecimal("500.00"))
                .build();

        when(repository.findById(ITEM_ID)).thenReturn(Optional.of(existingItem));
        when(repository.save(any(CatalogItem.class))).thenAnswer(i -> i.getArguments()[0]); // Retorna lo que guarda

        // WHEN
        CatalogItemResponse response = catalogService.updateItem(PROVIDER_ID, ITEM_ID, request);

        // THEN
        assertThat(response.getName()).isEqualTo("Nombre Nuevo");
        assertThat(response.getPrice()).isEqualTo(new BigDecimal("600.00"));

        // ✅ Verificar Evento ITEM_UPDATED
        verify(eventPublisher).publish(eq(PROVIDER_ID), eq("ITEM_UPDATED"), anyMap());
    }

    @Test
    @DisplayName("Debe IMPEDIR actualizar ítems de otro proveedor (Seguridad)")
    void updateItem_ShouldThrow_WhenNotOwner() {
        // GIVEN
        CatalogItem itemDeOtro = CatalogItem.builder().id(ITEM_ID).providerId(999L).build(); // Dueño 999
        when(repository.findById(ITEM_ID)).thenReturn(Optional.of(itemDeOtro));

        // WHEN & THEN
        CatalogItemRequest request = CatalogItemRequest.builder().build();

        assertThatThrownBy(() -> catalogService.updateItem(PROVIDER_ID, ITEM_ID, request)) // Intenta Provider 100
                .isInstanceOf(SecurityException.class);

        verify(repository, never()).save(any());
    }

    // ========================================================================
    // 🗑️ TEST: ELIMINACIÓN (SOFT DELETE)
    // ========================================================================

    @Test
    @DisplayName("Debe realizar SOFT DELETE (Archivar) y publicar evento")
    void deleteItem_ShouldArchiveAndPublishEvent() {
        // GIVEN
        CatalogItem item = CatalogItem.builder()
                .id(ITEM_ID)
                .providerId(PROVIDER_ID)
                .status(ItemStatus.ACTIVE)
                .build();

        when(repository.findById(ITEM_ID)).thenReturn(Optional.of(item));

        // WHEN
        catalogService.deleteItem(PROVIDER_ID, ITEM_ID);

        // THEN
        // 1. Verificar cambio de estado a ARCHIVED
        ArgumentCaptor<CatalogItem> captor = ArgumentCaptor.forClass(CatalogItem.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ItemStatus.ARCHIVED);

        // 2. ✅ Verificar Evento ITEM_ARCHIVED
        verify(eventPublisher).publish(eq(PROVIDER_ID), eq("ITEM_ARCHIVED"), anyMap());
    }

    // ========================================================================
    // 🎨 TEST: BRANDING
    // ========================================================================

    @Test
    @DisplayName("Debe ACTUALIZAR perfil de tienda y publicar evento")
    void updateStoreBranding_ShouldSucceed() {
        // GIVEN
        StoreProfile requestProfile = StoreProfile.builder()
                .displayName("Clínica House")
                .logoUrl("http://logo.png")
                .build();

        StoreProfile existingProfile = StoreProfile.builder().providerId(PROVIDER_ID).build();

        when(storeProfileRepository.findById(PROVIDER_ID)).thenReturn(Optional.of(existingProfile));
        when(storeProfileRepository.save(any())).thenAnswer(i -> i.getArguments()[0]);

        // WHEN
        catalogService.updateStoreBranding(PROVIDER_ID, requestProfile);

        // THEN
        verify(storeProfileRepository).save(any(StoreProfile.class));

        // ✅ Verificar Evento STORE_UPDATED
        verify(eventPublisher).publish(eq(PROVIDER_ID), eq("STORE_UPDATED"), anyMap());
    }

    // ========================================================================
    // 🔍 TEST: BÚSQUEDA PÚBLICA
    // ========================================================================

    @Test
    @DisplayName("GetItemDetail: Debe ocultar ítems ARCHIVADOS")
    void getItemDetail_ShouldThrow_WhenArchived() {
        // GIVEN
        CatalogItem archivedItem = CatalogItem.builder()
                .id(ITEM_ID)
                .status(ItemStatus.ARCHIVED)
                .build();

        when(repository.findById(ITEM_ID)).thenReturn(Optional.of(archivedItem));

        // WHEN & THEN
        assertThatThrownBy(() -> catalogService.getItemDetail(ITEM_ID, null, null))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("ya no está disponible");
    }
}
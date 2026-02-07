package com.quhealthy.catalog_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quhealthy.catalog_service.config.CustomAuthenticationToken;
import com.quhealthy.catalog_service.dto.CatalogItemRequest;
import com.quhealthy.catalog_service.dto.CatalogItemResponse;
import com.quhealthy.catalog_service.model.enums.ItemStatus;
import com.quhealthy.catalog_service.model.enums.ItemType;
import com.quhealthy.catalog_service.service.CatalogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CatalogController.class)
@AutoConfigureMockMvc(addFilters = false) // ⚠️ Desactivamos filtros de seguridad estándar para inyectar nuestro token manualmente
class CatalogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CatalogService catalogService;

    @Autowired
    private ObjectMapper objectMapper;

    // Constantes
    private static final Long PROVIDER_ID = 100L;
    private static final Long PLAN_ID = 2L;
    private static final Long ITEM_ID = 50L;

    @AfterEach
    void tearDown() {
        // Limpiar el contexto de seguridad después de cada test para no afectar a otros
        SecurityContextHolder.clearContext();
    }

    // ========================================================================
    // 🔐 TEST: ENDPOINTS PROTEGIDOS (Provider)
    // ========================================================================

    @Test
    @DisplayName("POST /items - Debe crear ítem cuando el token es válido")
    void createItem_ShouldReturnCreated() throws Exception {
        // GIVEN
        setupSecurityContext(); // Inyectamos el CustomAuthenticationToken

        CatalogItemRequest request = CatalogItemRequest.builder()
                .name("Consulta Dental")
                .type(ItemType.SERVICE)
                .price(new BigDecimal("500.00"))
                .description("Limpieza profunda")
                .build();

        CatalogItemResponse mockResponse = CatalogItemResponse.builder()
                .id(ITEM_ID)
                .name(request.getName())
                .price(request.getPrice())
                .status(ItemStatus.ACTIVE)
                .build();

        when(catalogService.createItem(eq(PROVIDER_ID), any(CatalogItemRequest.class), eq(PLAN_ID)))
                .thenReturn(mockResponse);

        // WHEN & THEN
        mockMvc.perform(post("/api/catalog/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(ITEM_ID))
                .andExpect(jsonPath("$.name").value("Consulta Dental"));
    }

    @Test
    @DisplayName("PUT /items/{id} - Debe actualizar ítem")
    void updateItem_ShouldReturnOk() throws Exception {
        // GIVEN
        setupSecurityContext();

        CatalogItemRequest request = CatalogItemRequest.builder()
                .name("Consulta Actualizada")
                .price(new BigDecimal("600.00"))
                .type(ItemType.SERVICE)
                .build();

        CatalogItemResponse mockResponse = CatalogItemResponse.builder()
                .id(ITEM_ID)
                .name("Consulta Actualizada")
                .price(new BigDecimal("600.00"))
                .build();

        when(catalogService.updateItem(eq(PROVIDER_ID), eq(ITEM_ID), any(CatalogItemRequest.class)))
                .thenReturn(mockResponse);

        // WHEN & THEN
        mockMvc.perform(put("/api/catalog/items/{id}", ITEM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Consulta Actualizada"));
    }

    @Test
    @DisplayName("DELETE /items/{id} - Debe archivar ítem")
    void deleteItem_ShouldReturnNoContent() throws Exception {
        // GIVEN
        setupSecurityContext();
        doNothing().when(catalogService).deleteItem(PROVIDER_ID, ITEM_ID);

        // WHEN & THEN
        mockMvc.perform(delete("/api/catalog/items/{id}", ITEM_ID))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("GET /me/items - Debe listar el catálogo del proveedor")
    void getMyCatalog_ShouldReturnPage() throws Exception {
        // GIVEN
        setupSecurityContext();
        Page<CatalogItemResponse> page = new PageImpl<>(List.of(
                CatalogItemResponse.builder().id(1L).name("Item 1").build()
        ));

        when(catalogService.getProviderCatalog(eq(PROVIDER_ID), isNull(), any(Pageable.class)))
                .thenReturn(page);

        // WHEN & THEN
        mockMvc.perform(get("/api/catalog/me/items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Item 1"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("Debe fallar (403/500) si el token no es CustomAuthenticationToken")
    void protectedEndpoint_ShouldFail_WhenTokenIsInvalid() throws Exception {
        // NO llamamos a setupSecurityContext(), por lo que el contexto está vacío o es genérico.
        // El controller lanzará SecurityException "Sesión no válida".
        // Al tener addFilters=false, la excepción salta y Spring la maneja.

        CatalogItemRequest request = CatalogItemRequest.builder().name("Test").type(ItemType.PRODUCT).build();

        mockMvc.perform(post("/api/catalog/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError()) // O el status que maneje tu GlobalExceptionHandler para SecurityException
                .andExpect(result -> {
                    // Verificamos que la causa fue la validación de sesión
                    if (result.getResolvedException() != null) {
                        String msg = result.getResolvedException().getMessage();
                        assert(msg.contains("Sesión no válida"));
                    }
                });
    }

    // ========================================================================
    // 🌍 TEST: ENDPOINTS PÚBLICOS (Sin Auth)
    // ========================================================================

    @Test
    @DisplayName("GET /items/{id} - Debe retornar detalle público")
    void getItemDetail_ShouldReturnItem() throws Exception {
        // GIVEN
        CatalogItemResponse response = CatalogItemResponse.builder().id(ITEM_ID).name("Público").build();
        when(catalogService.getItemDetail(eq(ITEM_ID), isNull(), isNull())).thenReturn(response);

        // WHEN & THEN
        mockMvc.perform(get("/api/catalog/items/{id}", ITEM_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Público"));
    }

    @Test
    @DisplayName("GET /nearby - Debe requerir lat/lng")
    void getNearby_ShouldFail_WhenParamsMissing() throws Exception {
        mockMvc.perform(get("/api/catalog/nearby"))
                .andExpect(status().isBadRequest()); // Falta lat y lng
    }

    @Test
    @DisplayName("GET /nearby - Debe retornar items cercanos")
    void getNearby_ShouldReturnItems_WhenParamsValid() throws Exception {
        // GIVEN
        Page<CatalogItemResponse> page = new PageImpl<>(Collections.emptyList());
        when(catalogService.getNearbyItems(eq(19.43), eq(-99.13), anyDouble(), any(Pageable.class)))
                .thenReturn(page);

        // WHEN & THEN
        mockMvc.perform(get("/api/catalog/nearby")
                        .param("lat", "19.43")
                        .param("lng", "-99.13")
                        .param("radiusKm", "5.0"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /provider/{id}/items - Debe listar tienda pública")
    void getProviderStore_ShouldReturnPage() throws Exception {
        // GIVEN
        Page<CatalogItemResponse> page = new PageImpl<>(List.of(
                CatalogItemResponse.builder().id(2L).name("Item Tienda").build()
        ));
        when(catalogService.getProviderCatalog(eq(PROVIDER_ID), eq("SALUD"), any(Pageable.class)))
                .thenReturn(page);

        // WHEN & THEN
        mockMvc.perform(get("/api/catalog/provider/{id}/items", PROVIDER_ID)
                        .param("category", "SALUD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Item Tienda"));
    }

    // ========================================================================
    // 🛠️ HELPER: Configuración de Token
    // ========================================================================

    /**
     * Simula que un usuario Provider (con Plan ID 2) ha iniciado sesión.
     * Esto llena el SecurityContext con el CustomAuthenticationToken que espera el Controller.
     */
    private void setupSecurityContext() {
        CustomAuthenticationToken authToken = new CustomAuthenticationToken(
                PROVIDER_ID, // Principal
                null,        // Credentials
                List.of(new SimpleGrantedAuthority("ROLE_PROVIDER")), // Authorities
                PLAN_ID,     // Plan ID
                "COMPLETED", // Onboarding Status
                "APPROVED"   // KYC Status
        );

        SecurityContextHolder.getContext().setAuthentication(authToken);
    }
}
package com.quhealthy.catalog_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quhealthy.catalog_service.config.CustomAuthenticationToken;
import com.quhealthy.catalog_service.config.TestConfig; // ✅ IMPORTANTE: Tu config de test
import com.quhealthy.catalog_service.model.StoreProfile;
import com.quhealthy.catalog_service.service.CatalogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import; // ✅ NECESARIO
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles; // ✅ NECESARIO
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StoreProfileController.class)
@AutoConfigureMockMvc(addFilters = false) // Desactivamos filtros de seguridad para inyectar token manualmente
@Import(TestConfig.class) // ✅ SOLUCIÓN AL ERROR: Cargamos el Bean de ObjectMapper y Mocks
@ActiveProfiles("test")   // ✅ Aseguramos entorno de prueba
class StoreProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean // Nueva anotación de Spring Boot 3.4+ (Reemplaza a @MockBean)
    private CatalogService catalogService;

    @Autowired
    private ObjectMapper objectMapper;

    // Constantes
    private static final Long PROVIDER_ID = 100L;
    private static final Long PLAN_ID = 2L;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ========================================================================
    // 🔐 TEST: ACTUALIZAR BRANDING (Protegido)
    // ========================================================================

    @Test
    @DisplayName("PUT /me - Debe actualizar branding si hay sesión válida")
    void updateMyBranding_ShouldReturnUpdatedProfile() throws Exception {
        // GIVEN
        setupSecurityContext(); // Simulamos usuario logueado

        StoreProfile requestProfile = StoreProfile.builder()
                .displayName("Clínica House")
                .primaryColor("#FF0000")
                .secondaryColor("#00FF00")
                .bio("Diagnósticos diferenciales")
                .whatsappEnabled(true)
                .showLocation(true)
                .build();

        // El servicio retorna el objeto actualizado
        when(catalogService.updateStoreBranding(eq(PROVIDER_ID), any(StoreProfile.class)))
                .thenReturn(requestProfile);

        // WHEN & THEN
        mockMvc.perform(put("/api/store/profile/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestProfile)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Clínica House"))
                .andExpect(jsonPath("$.primaryColor").value("#FF0000"))
                .andExpect(jsonPath("$.whatsappEnabled").value(true));
    }

    @Test
    @DisplayName("PUT /me - Debe fallar si no hay token (SecurityException)")
    void updateMyBranding_ShouldFail_WhenNoToken() throws Exception {
        // GIVEN: NO llamamos a setupSecurityContext()
        StoreProfile requestProfile = StoreProfile.builder().displayName("Hacker Store").build();

        // WHEN & THEN
        // El controller lanza "SecurityException: Sesión no válida"
        mockMvc.perform(put("/api/store/profile/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestProfile)))
                .andExpect(status().isInternalServerError()) // O el error que tu ExceptionHandler maneje
                .andExpect(result -> {
                    if (result.getResolvedException() != null) {
                        String msg = result.getResolvedException().getMessage();
                        assert(msg.contains("Sesión no válida"));
                    }
                });
    }

    // ========================================================================
    // 🌍 TEST: VER PERFIL (Público)
    // ========================================================================

    @Test
    @DisplayName("GET /{id} - Debe devolver perfil público sin necesidad de login")
    void getStoreBranding_ShouldReturnProfile() throws Exception {
        // GIVEN
        StoreProfile mockProfile = StoreProfile.builder()
                .providerId(PROVIDER_ID)
                .displayName("Tienda Pública")
                .logoUrl("http://img.com/logo.png")
                .build();

        when(catalogService.getStoreProfile(PROVIDER_ID)).thenReturn(mockProfile);

        // WHEN & THEN
        // Nota: No configuramos SecurityContext aquí, probando que es público
        mockMvc.perform(get("/api/store/profile/{id}", PROVIDER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Tienda Pública"))
                .andExpect(jsonPath("$.logoUrl").value("http://img.com/logo.png"));
    }

    // ========================================================================
    // 🛠️ HELPER
    // ========================================================================

    private void setupSecurityContext() {
        CustomAuthenticationToken authToken = new CustomAuthenticationToken(
                PROVIDER_ID, // Principal
                null,        // Credentials
                List.of(new SimpleGrantedAuthority("ROLE_PROVIDER")), // Authorities
                PLAN_ID,     // Plan ID
                "COMPLETED", // Onboarding
                "APPROVED"   // KYC
        );

        SecurityContextHolder.getContext().setAuthentication(authToken);
    }
}
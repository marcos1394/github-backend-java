package com.quhealthy.onboarding_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quhealthy.onboarding_service.config.TestConfig;
import com.quhealthy.onboarding_service.dto.request.UpdateProfileRequest;
import com.quhealthy.onboarding_service.model.ProviderProfile;
import com.quhealthy.onboarding_service.repository.ProviderProfileRepository;
import com.quhealthy.onboarding_service.service.ProfileService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProfileController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import(TestConfig.class)
class ProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // 🧠 MOCKS
    @MockitoBean
    private ProfileService profileService;

    @MockitoBean
    private ProviderProfileRepository profileRepository;

    private static final Long USER_ID = 101L;

    // ========================================================================
    // ✅ PUT: ACTUALIZAR PERFIL
    // ========================================================================

    @Test
    @DisplayName("PUT /profile - Should return 200 when request is valid")
    void updateProfile_ShouldReturn200() throws Exception {
        // GIVEN
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setBusinessName("Consultorio Dr. Test");
        request.setBio("Bio de prueba 1234567890"); // > 20 chars
        request.setProfileImageUrl("http://img.com");
        request.setAddress("Calle Test 123");
        request.setLatitude(19.0);
        request.setLongitude(-99.0);
        request.setCategoryId(1L);
        request.setSubCategoryId(2L);
        request.setContactPhone("555-5555");
        request.setWebsiteUrl("http://drtest.com");

        // WHEN & THEN
        mockMvc.perform(put("/api/onboarding/profile")
                        .header("X-User-Id", USER_ID) // Header obligatorio
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // Verificar que se llamó al servicio con el ID correcto
        verify(profileService).updateProfile(eq(USER_ID), any(UpdateProfileRequest.class));
    }

    @Test
    @DisplayName("PUT /profile - Should return 400 Bad Request when validation fails")
    void updateProfile_ShouldReturn400_WhenInvalid() throws Exception {
        // GIVEN - Request vacío/invalido (sin BusinessName, sin Lat/Long)
        UpdateProfileRequest invalidRequest = new UpdateProfileRequest();

        // WHEN & THEN
        mockMvc.perform(put("/api/onboarding/profile")
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    // ========================================================================
    // ✅ GET: OBTENER PERFIL
    // ========================================================================

    @Test
    @DisplayName("GET /profile - Should return 200 and Profile JSON when exists")
    void getProfile_ShouldReturnProfile() throws Exception {
        // GIVEN
        ProviderProfile mockProfile = ProviderProfile.builder()
                .providerId(USER_ID)
                .businessName("Clinica Real")
                .bio("Bio real")
                .address("Av. Reforma 1")
                .latitude(20.0)
                .longitude(-100.0)
                .websiteUrl("https://clinica.com")
                .contactPhone("999-999")
                .categoryId(5L)
                .subCategoryId(10L)
                .slug("clinica-real-101")
                .build();

        when(profileRepository.findById(USER_ID)).thenReturn(Optional.of(mockProfile));

        // WHEN & THEN
        mockMvc.perform(get("/api/onboarding/profile")
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providerId").value(USER_ID))
                .andExpect(jsonPath("$.businessName").value("Clinica Real"))
                .andExpect(jsonPath("$.websiteUrl").value("https://clinica.com"))
                .andExpect(jsonPath("$.slug").value("clinica-real-101"));
    }

    @Test
    @DisplayName("GET /profile - Should return 404 Not Found when profile does not exist")
    void getProfile_ShouldReturn404() throws Exception {
        // GIVEN
        when(profileRepository.findById(USER_ID)).thenReturn(Optional.empty());

        // WHEN & THEN
        mockMvc.perform(get("/api/onboarding/profile")
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isNotFound());
    }
}
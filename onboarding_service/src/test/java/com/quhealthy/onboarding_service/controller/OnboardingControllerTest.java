package com.quhealthy.onboarding_service.controller;

import com.quhealthy.onboarding_service.config.TestConfig;
import com.quhealthy.onboarding_service.model.ProviderOnboarding;
import com.quhealthy.onboarding_service.model.enums.OnboardingStatus;
import com.quhealthy.onboarding_service.repository.ProviderOnboardingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OnboardingController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import(TestConfig.class)
class OnboardingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProviderOnboardingRepository onboardingRepository;

    private static final Long USER_ID = 303L;

    // ========================================================================
    // 🆕 USUARIO NUEVO (PRIMERA VEZ)
    // ========================================================================

    @Test
    @DisplayName("GET /status - Should return default status (0%) for new user")
    void getStatus_ShouldReturnDefault_WhenUserIsNew() throws Exception {
        // GIVEN - Repositorio devuelve vacío
        when(onboardingRepository.findById(USER_ID)).thenReturn(Optional.empty());

        // WHEN & THEN
        mockMvc.perform(get("/api/onboarding/status")
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providerId").value(USER_ID))
                .andExpect(jsonPath("$.profileStatus").value("PENDING"))
                .andExpect(jsonPath("$.completionPercentage").value(0));
    }

    // ========================================================================
    // 🚦 USUARIO EN PROGRESO
    // ========================================================================

    @Test
    @DisplayName("GET /status - Should calculate percentage correctly (e.g. 66%)")
    void getStatus_ShouldCalculatePercentage() throws Exception {
        // GIVEN - Usuario con Perfil y KYC completados, pero sin Licencia
        ProviderOnboarding existingStatus = ProviderOnboarding.builder()
                .providerId(USER_ID)
                .profileStatus(OnboardingStatus.COMPLETED) // 1/3
                .kycStatus(OnboardingStatus.COMPLETED)     // 2/3
                .licenseStatus(OnboardingStatus.PENDING)   // 0
                .build();

        when(onboardingRepository.findById(USER_ID)).thenReturn(Optional.of(existingStatus));

        // WHEN & THEN
        // Cálculo esperado: (2 * 100) / 3 = 66
        mockMvc.perform(get("/api/onboarding/status")
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.kycStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.licenseStatus").value("PENDING"))
                .andExpect(jsonPath("$.completionPercentage").value(66));
    }

    // ========================================================================
    // 🚫 USUARIO CON RECHAZOS
    // ========================================================================

    @Test
    @DisplayName("GET /status - Should return rejection reasons map")
    void getStatus_ShouldReturnRejectionReasons() throws Exception {
        // GIVEN - Usuario rechazado en KYC
        ProviderOnboarding rejectedStatus = ProviderOnboarding.builder()
                .providerId(USER_ID)
                .profileStatus(OnboardingStatus.COMPLETED)
                .kycStatus(OnboardingStatus.ACTION_REQUIRED) // Rechazado
                .rejectionReasons(Map.of("KYC", "Foto borrosa"))
                .build();

        when(onboardingRepository.findById(USER_ID)).thenReturn(Optional.of(rejectedStatus));

        // WHEN & THEN
        mockMvc.perform(get("/api/onboarding/status")
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kycStatus").value("ACTION_REQUIRED"))
                .andExpect(jsonPath("$.rejectionReasons.KYC").value("Foto borrosa"));
    }
}
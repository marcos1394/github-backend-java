package com.quhealthy.onboarding_service.controller;

import com.quhealthy.onboarding_service.config.TestConfig;
import com.quhealthy.onboarding_service.dto.response.LicenseResponse;
import com.quhealthy.onboarding_service.service.LicenseService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LicenseController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import(TestConfig.class)
class LicenseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LicenseService licenseService;

    private static final Long USER_ID = 202L;

    // ========================================================================
    // ✅ UPLOAD EXITOSO
    // ========================================================================

    @Test
    @DisplayName("POST /upload - Should return 200 and License Response when file is valid")
    void uploadLicense_ShouldReturn200() throws Exception {
        // GIVEN
        MockMultipartFile file = new MockMultipartFile(
                "file",           // Nombre del parámetro @RequestPart
                "cedula.pdf",     // Nombre original
                "application/pdf", // Content Type
                new byte[]{10, 20, 30} // Dummy content
        );

        // Respuesta simulada del servicio
        LicenseResponse mockResponse = LicenseResponse.builder()
                .licenseNumber("12345678")
                .careerName("MEDICO CIRUJANO")
                .institutionName("UNAM")
                .status("APPROVED")
                .documentUrl("http://storage/cedula.pdf")
                .build();

        when(licenseService.uploadAndVerifyLicense(eq(USER_ID), any())).thenReturn(mockResponse);

        // WHEN & THEN
        mockMvc.perform(multipart("/api/onboarding/license/upload")
                        .file(file)
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.licenseNumber").value("12345678"))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.careerName").value("MEDICO CIRUJANO"));

        verify(licenseService).uploadAndVerifyLicense(eq(USER_ID), any());
    }

    // ========================================================================
    // ❌ VALIDACIONES DE ARCHIVO
    // ========================================================================

    @Test
    @DisplayName("POST /upload - Should return 400 Bad Request when file is empty")
    void uploadLicense_ShouldReturn400_WhenFileIsEmpty() throws Exception {
        // GIVEN - Archivo vacío
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "empty.pdf", "application/pdf", new byte[0]
        );

        // WHEN & THEN
        mockMvc.perform(multipart("/api/onboarding/license/upload")
                        .file(emptyFile)
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isBadRequest()); // Disparado por if (file.isEmpty())
    }

    @Test
    @DisplayName("POST /upload - Should return 400 Bad Request when 'file' part is missing")
    void uploadLicense_ShouldReturn400_WhenPartMissing() throws Exception {
        // Intentamos hacer un multipart sin adjuntar el archivo "file"
        mockMvc.perform(multipart("/api/onboarding/license/upload")
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isBadRequest());
    }

    // ========================================================================
    // 🔒 SEGURIDAD (HEADER)
    // ========================================================================

    @Test
    @DisplayName("POST /upload - Should return 400 Bad Request when X-User-Id header is missing")
    void uploadLicense_ShouldReturn400_WhenHeaderMissing() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "cedula.jpg", "image/jpeg", new byte[]{1}
        );

        mockMvc.perform(multipart("/api/onboarding/license/upload")
                        .file(file))
                .andExpect(status().isBadRequest());
    }
}
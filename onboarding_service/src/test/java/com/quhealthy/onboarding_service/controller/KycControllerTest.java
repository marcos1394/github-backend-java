package com.quhealthy.onboarding_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quhealthy.onboarding_service.config.TestConfig;
import com.quhealthy.onboarding_service.dto.response.KycDocumentResponse;
import com.quhealthy.onboarding_service.model.enums.DocumentType;
import com.quhealthy.onboarding_service.service.KycService;
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

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(KycController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import(TestConfig.class)
class KycControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private KycService kycService;

    private static final Long USER_ID = 505L;

    // ========================================================================
    // ✅ UPLOAD EXITOSO
    // ========================================================================

    @Test
    @DisplayName("POST /upload - Should return 200 and Analysis Result when file is valid")
    void uploadDocument_ShouldReturn200() throws Exception {
        // GIVEN
        // Simulamos un archivo real
        MockMultipartFile file = new MockMultipartFile(
                "file",           // Nombre del parámetro (@RequestPart)
                "ine.jpg",        // Nombre original
                "image/jpeg",     // Content Type
                new byte[]{1, 2, 3, 4} // Contenido dummy
        );

        DocumentType type = DocumentType.INE_FRONT;

        // Respuesta simulada del servicio
        KycDocumentResponse mockResponse = KycDocumentResponse.builder()
                .documentType("INE_FRONT")
                .verificationStatus("APPROVED")
                .fileUrl("http://storage.com/ine.jpg")
                .extractedData(Map.of("curp", "ABCD123"))
                .build();

        when(kycService.uploadAndVerifyDocument(eq(USER_ID), any(), eq(type)))
                .thenReturn(mockResponse);

        // WHEN & THEN
        // Nota: multipart(...) envía por defecto un POST
        mockMvc.perform(multipart("/api/onboarding/kyc/upload")
                        .file(file)
                        .param("type", "INE_FRONT") // Enum como String
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentType").value("INE_FRONT"))
                .andExpect(jsonPath("$.verificationStatus").value("APPROVED"))
                .andExpect(jsonPath("$.extractedData.curp").value("ABCD123"));

        verify(kycService).uploadAndVerifyDocument(eq(USER_ID), any(), eq(type));
    }

    // ========================================================================
    // ❌ VALIDACIONES DE ARCHIVO
    // ========================================================================

    @Test
    @DisplayName("POST /upload - Should return 400 Bad Request when file is empty")
    void uploadDocument_ShouldReturn400_WhenFileIsEmpty() throws Exception {
        // GIVEN - Archivo con 0 bytes
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "empty.txt", "text/plain", new byte[0]
        );

        // WHEN & THEN
        mockMvc.perform(multipart("/api/onboarding/kyc/upload")
                        .file(emptyFile)
                        .param("type", "INE_FRONT")
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isBadRequest()); // Activado por tu if (file.isEmpty())
    }

    @Test
    @DisplayName("POST /upload - Should return 400 when 'type' param is invalid enum")
    void uploadDocument_ShouldReturn400_WhenTypeIsInvalid() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "doc.jpg", "image/jpeg", new byte[]{1}
        );

        // Enviamos "LICENCIA_CONDUCIR" que no existe en el Enum DocumentType
        mockMvc.perform(multipart("/api/onboarding/kyc/upload")
                        .file(file)
                        .param("type", "INVALID_TYPE")
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isBadRequest());
    }

    // ========================================================================
    // 🔒 SEGURIDAD (HEADER)
    // ========================================================================

    @Test
    @DisplayName("POST /upload - Should return 400 Bad Request when X-User-Id is missing")
    void uploadDocument_ShouldReturn400_WhenHeaderMissing() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "doc.jpg", "image/jpeg", new byte[]{1}
        );

        mockMvc.perform(multipart("/api/onboarding/kyc/upload")
                        .file(file)
                        .param("type", "PASSPORT")) // Falta el header
                .andExpect(status().isBadRequest());
    }
}
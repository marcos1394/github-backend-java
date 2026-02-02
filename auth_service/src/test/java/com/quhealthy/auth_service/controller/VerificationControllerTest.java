package com.quhealthy.auth_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quhealthy.auth_service.config.TestConfig;
import com.quhealthy.auth_service.dto.request.*;
import com.quhealthy.auth_service.dto.response.MessageResponse;
import com.quhealthy.auth_service.service.VerificationService;
import com.quhealthy.auth_service.service.security.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(VerificationController.class)
@AutoConfigureMockMvc(addFilters = false) // Desactiva seguridad JWT para probar solo lógica del controller
@ActiveProfiles("test")
@Import(TestConfig.class) // ✅ Importamos la configuración centralizada (Jackson, GCP, PubSub)
@TestPropertySource(properties = {
        "spring.cloud.gcp.core.enabled=false",
        "spring.cloud.gcp.pubsub.enabled=false"
})
class VerificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // ========================================================================
    // 🧠 MOCKS DE NEGOCIO
    // ========================================================================
    @MockitoBean
    private VerificationService verificationService;

    // ========================================================================
    // 🛡️ MOCKS DE INFRAESTRUCTURA (Necesarios para levantar el contexto)
    // ========================================================================
    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @MockitoBean
    private PasswordEncoder passwordEncoder;

    // ========================================================================
    // ✅ TEST: VERIFICAR EMAIL
    // ========================================================================

    @Test
    @DisplayName("POST /verify-email - Should return 200 OK when token is valid")
    void verifyEmail_ShouldReturn200() throws Exception {
        // Arrange
        VerifyEmailRequest request = VerifyEmailRequest.builder()
                .token("valid-uuid-token-123")
                .build();

        MessageResponse response = MessageResponse.builder()
                .message("Email verified successfully")
                .build();

        when(verificationService.verifyEmail(any(VerifyEmailRequest.class)))
                .thenReturn(response);

        // Act & Assert
        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Email verified successfully"));
    }

    @Test
    @DisplayName("POST /verify-email - Should return 400 when token is missing")
    void verifyEmail_ShouldReturn400_WhenInvalid() throws Exception {
        VerifyEmailRequest request = VerifyEmailRequest.builder()
                .token("") // Token vacío debe fallar @Valid
                .build();

        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ========================================================================
    // ✅ TEST: VERIFICAR TELÉFONO
    // ========================================================================

    @Test
    @DisplayName("POST /verify-phone - Should return 200 OK when code is valid")
    void verifyPhone_ShouldReturn200() throws Exception {
        // Arrange
        VerifyPhoneRequest request = VerifyPhoneRequest.builder()
                .identifier("+521234567890")
                .code("123456")
                .build();

        MessageResponse response = MessageResponse.builder()
                .message("Phone verified successfully")
                .build();

        when(verificationService.verifyPhone(any(VerifyPhoneRequest.class)))
                .thenReturn(response);

        // Act & Assert
        mockMvc.perform(post("/api/auth/verify-phone")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Phone verified successfully"));
    }

    // ========================================================================
    // 🔄 TEST: REENVIAR VERIFICACIÓN
    // ========================================================================

    @Test
    @DisplayName("POST /resend-verification - Should return 200 OK")
    void resendVerification_ShouldReturn200() throws Exception {
        // Arrange
        ResendVerificationRequest request = ResendVerificationRequest.builder()
                .email("test@patient.com")
                .type("EMAIL") // ✅ CORRECCIÓN: Usamos String en lugar de Enum
                .build();

        MessageResponse response = MessageResponse.builder()
                .message("Verification code resent")
                .build();

        when(verificationService.resendVerification(any(ResendVerificationRequest.class)))
                .thenReturn(response);

        // Act & Assert
        mockMvc.perform(post("/api/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Verification code resent"));
    }

    // ========================================================================
    // 🔑 TEST: FORGOT PASSWORD
    // ========================================================================

    @Test
    @DisplayName("POST /forgot-password - Should return 200 OK")
    void forgotPassword_ShouldReturn200() throws Exception {
        // Arrange
        ForgotPasswordRequest request = ForgotPasswordRequest.builder()
                .email("test@patient.com")
                .build();

        MessageResponse response = MessageResponse.builder()
                .message("Reset link sent")
                .build();

        when(verificationService.forgotPassword(any(ForgotPasswordRequest.class)))
                .thenReturn(response);

        // Act & Assert
        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Reset link sent"));
    }

    // ========================================================================
    // 🔒 TEST: RESET PASSWORD
    // ========================================================================

    @Test
    @DisplayName("POST /reset-password - Should return 200 OK")
    void resetPassword_ShouldReturn200() throws Exception {
        // Arrange
        ResetPasswordRequest request = ResetPasswordRequest.builder()
                .token("valid-reset-token")
                .newPassword("NewPassword123!")
                .build();

        MessageResponse response = MessageResponse.builder()
                .message("Password updated successfully")
                .build();

        when(verificationService.resetPassword(any(ResetPasswordRequest.class)))
                .thenReturn(response);

        // Act & Assert
        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password updated successfully"));
    }
}
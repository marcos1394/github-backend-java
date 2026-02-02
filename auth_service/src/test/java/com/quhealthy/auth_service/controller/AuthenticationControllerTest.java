package com.quhealthy.auth_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quhealthy.auth_service.config.TestConfig;
import com.quhealthy.auth_service.dto.request.LoginRequest;
import com.quhealthy.auth_service.dto.response.AuthResponse;
import com.quhealthy.auth_service.service.AuthenticationService;
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

@WebMvcTest(AuthenticationController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import(TestConfig.class)
@TestPropertySource(properties = {
        "spring.cloud.gcp.core.enabled=false",
        "spring.cloud.gcp.pubsub.enabled=false"
})
class AuthenticationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthenticationService authenticationService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @MockitoBean
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("POST /login - Should return 200 OK with Token when credentials are valid")
    void login_ShouldReturn200() throws Exception {
        // Arrange
        LoginRequest request = LoginRequest.builder()
                .email("test@patient.com")
                .password("Password123!")
                .build();

        // ✅ CORRECCIÓN: Ajustamos el mock a la estructura real del JSON que vimos en el log
        AuthResponse response = AuthResponse.builder()
                .token("mock.jwt.token.xyz")        // Antes intentábamos accessToken
                .refreshToken("mock.refresh.token.abc")
                .type("Bearer")
                .message("Login successful")
                .build();

        when(authenticationService.authenticate(any(LoginRequest.class)))
                .thenReturn(response);

        // Act & Assert
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                // ✅ CORRECCIÓN: Buscamos "token", no "accessToken"
                .andExpect(jsonPath("$.token").value("mock.jwt.token.xyz"))
                .andExpect(jsonPath("$.refreshToken").value("mock.refresh.token.abc"));
    }

    @Test
    @DisplayName("POST /login - Should return 400 Bad Request on invalid format")
    void login_ShouldReturn400_WhenEmailInvalid() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .email("not-an-email")
                .password("123")
                .build();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
package com.quhealthy.auth_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quhealthy.auth_service.config.TestConfig; // <--- USAMOS TU CONFIG CENTRALIZADA
import com.quhealthy.auth_service.dto.request.RegisterConsumerRequest;
import com.quhealthy.auth_service.dto.request.RegisterProviderRequest;
import com.quhealthy.auth_service.dto.response.ConsumerRegistrationResponse;
import com.quhealthy.auth_service.dto.response.ProviderRegistrationResponse;
import com.quhealthy.auth_service.service.RegistrationService;
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

@WebMvcTest(RegistrationController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test") // Activa el perfil "test" para que TestConfig se cargue
@Import(TestConfig.class) // Importa explícitamente tus Mocks globales (Jackson, GCP, PubSub)
@TestPropertySource(properties = {
        "spring.cloud.gcp.core.enabled=false",
        "spring.cloud.gcp.pubsub.enabled=false"
})
class RegistrationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper; // Se inyectará desde TestConfig

    // ========================================================================
    // 🧠 MOCKS DE NEGOCIO
    // ========================================================================
    @MockitoBean
    private RegistrationService registrationService;

    // ========================================================================
    // 🛡️ MOCKS DE SEGURIDAD (Requeridos para levantar el contexto Web)
    // ========================================================================
    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @MockitoBean
    private PasswordEncoder passwordEncoder;

    // ========================================================================
    // 🧪 TESTS
    // ========================================================================

    @Test
    @DisplayName("POST /register/consumer - Should return 201 Created when valid")
    void registerConsumer_ShouldReturn201() throws Exception {
        RegisterConsumerRequest request = RegisterConsumerRequest.builder()
                .email("test@patient.com")
                .password("Pass123!")
                .firstName("Juan")
                .lastName("Perez")
                .termsAccepted(true)
                .build();

        ConsumerRegistrationResponse response = ConsumerRegistrationResponse.builder()
                .id(1L)
                .email("test@patient.com")
                .message("Success")
                .build();

        when(registrationService.registerConsumer(any(RegisterConsumerRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/auth/register/consumer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("test@patient.com"));
    }

    @Test
    @DisplayName("POST /register/consumer - Should return 400 Bad Request on invalid input")
    void registerConsumer_ShouldReturn400_WhenEmailInvalid() throws Exception {
        RegisterConsumerRequest request = RegisterConsumerRequest.builder()
                .email("not-an-email")
                .firstName("Juan")
                .lastName("Perez")
                .build();

        mockMvc.perform(post("/api/auth/register/consumer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /register/provider - Should return 201 Created when valid")
    void registerProvider_ShouldReturn201() throws Exception {
        RegisterProviderRequest request = RegisterProviderRequest.builder()
                .email("dr@house.com")
                .password("Pass123!")
                .firstName("Greg")
                .lastName("House")
                .businessName("Clinic")
                .phone("+521234567890")
                .parentCategoryId(10L)
                .termsAccepted(true)
                .build();

        ProviderRegistrationResponse response = ProviderRegistrationResponse.builder()
                .id(2L)
                .email("dr@house.com")
                .build();

        when(registrationService.registerProvider(any(RegisterProviderRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/auth/register/provider")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("dr@house.com"));
    }
}
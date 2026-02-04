package com.quhealthy.notification_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quhealthy.notification_service.config.TestConfig; // Importamos tu config global de test
import com.quhealthy.notification_service.dto.NotificationResponse;
import com.quhealthy.notification_service.dto.UnreadCountResponse;
import com.quhealthy.notification_service.model.enums.NotificationType;
import com.quhealthy.notification_service.model.enums.TargetRole;
import com.quhealthy.notification_service.service.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(NotificationController.class) // 🚀 Slice Test: Solo carga este Controller
@AutoConfigureMockMvc(addFilters = false) // Desactivamos filtros de seguridad (JWT) para probar solo lógica
@ActiveProfiles("test")
@Import(TestConfig.class) // Importamos los Mocks globales (Firebase, GCP, Jackson)
@TestPropertySource(properties = {
        "spring.cloud.gcp.pubsub.enabled=false" // Aseguramos que PubSub esté apagado
})
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // ========================================================================
    // 🧠 MOCKS DE NEGOCIO (Usando la nueva anotación de Spring Boot 3.4)
    // ========================================================================
    @MockitoBean
    private NotificationService notificationService;

    // ========================================================================
    // 🧪 TESTS
    // ========================================================================

    @Test
    @DisplayName("GET /api/notifications - Should return 200 and list for CONSUMER")
    void getMyNotifications_ShouldReturnList() throws Exception {
        // Arrange
        Long userId = 1L;
        TargetRole role = TargetRole.CONSUMER;

        // Simulamos la autenticación manual ya que addFilters=false
        Authentication auth = createAuth(userId, "ROLE_CONSUMER");

        NotificationResponse dto = NotificationResponse.builder()
                .id(10L)
                .title("Cita Confirmada")
                .type(NotificationType.SUCCESS)
                .isRead(false)
                .createdAt(LocalDateTime.now())
                .build();

        when(notificationService.getUserNotifications(eq(userId), eq(role), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(dto)));

        // Act & Assert
        mockMvc.perform(get("/api/notifications")
                        .principal(auth) // Inyectamos el usuario simulado
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(10))
                .andExpect(jsonPath("$.content[0].title").value("Cita Confirmada"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /api/notifications - Should return 200 and detect PROVIDER role")
    void getMyNotifications_ShouldDetectProviderRole() throws Exception {
        // Arrange
        Long userId = 55L;
        // Simulamos autenticación como Médico
        Authentication auth = createAuth(userId, "ROLE_PROVIDER");

        when(notificationService.getUserNotifications(eq(userId), eq(TargetRole.PROVIDER), any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        // Act & Assert
        mockMvc.perform(get("/api/notifications")
                        .principal(auth)
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk());

        // Verificamos que el Controller extrajo correctamente el enum TargetRole.PROVIDER
        verify(notificationService).getUserNotifications(eq(userId), eq(TargetRole.PROVIDER), any(PageRequest.class));
    }

    @Test
    @DisplayName("GET /unread-count - Should return correct count")
    void getUnreadCount_ShouldReturnCount() throws Exception {
        // Arrange
        Long userId = 1L;
        Authentication auth = createAuth(userId, "ROLE_CONSUMER");

        when(notificationService.getUnreadCount(userId, TargetRole.CONSUMER))
                .thenReturn(UnreadCountResponse.builder().unreadCount(5L).build());

        // Act & Assert
        mockMvc.perform(get("/api/notifications/unread-count")
                        .principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(5));
    }

    @Test
    @DisplayName("PUT /read-all - Should call service markAllAsRead")
    void markAllAsRead_ShouldReturn204() throws Exception {
        // Arrange
        Long userId = 200L;
        Authentication auth = createAuth(userId, "ROLE_PROVIDER");

        // Act & Assert
        mockMvc.perform(put("/api/notifications/read-all")
                        .principal(auth))
                .andExpect(status().isNoContent());

        verify(notificationService).markAllAsRead(userId, TargetRole.PROVIDER);
    }

    @Test
    @DisplayName("PUT /{id}/read - Should mark single notification as read")
    void markOneAsRead_ShouldReturn204() throws Exception {
        // Arrange
        Long userId = 100L;
        Long notifId = 999L;
        Authentication auth = createAuth(userId, "ROLE_CONSUMER");

        // Act & Assert
        mockMvc.perform(put("/api/notifications/{id}/read", notifId)
                        .principal(auth))
                .andExpect(status().isNoContent());

        verify(notificationService).markOneAsRead(notifId, userId, TargetRole.CONSUMER);
    }

    // ========================================================================
    // 🛠️ HELPERS
    // ========================================================================

    /**
     * Crea un objeto de Autenticación simulado compatible con @AuthenticationPrincipal
     * y la lógica de extracción de roles del Controller.
     */
    private Authentication createAuth(Long userId, String role) {
        // TestingAuthenticationToken es una implementación simple provista por Spring Test
        return new TestingAuthenticationToken(
                userId, // Principal (El ID del usuario)
                null,   // Credentials
                List.of(new SimpleGrantedAuthority(role)) // Authorities
        );
    }
}
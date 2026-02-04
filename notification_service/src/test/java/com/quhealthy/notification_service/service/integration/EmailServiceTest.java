package com.quhealthy.notification_service.service.integration;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.Emails;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    // El servicio a probar
    private EmailService emailService;

    // Mocks de la librería Resend
    @Mock private Resend resendClientMock;
    @Mock private Emails emailsOperationMock; // La clase que maneja .emails()

    @BeforeEach
    void setUp() {
        emailService = new EmailService();
    }

    // =========================================================================
    // ✅ ESCENARIO 1: INICIALIZACIÓN (init)
    // =========================================================================

    @Test
    @DisplayName("init() debe habilitar el servicio si la API Key existe")
    void shouldEnableServiceWhenApiKeyIsPresent() {
        // Arrange
        injectProperties("re_123_test_key", "no-reply@test.com");

        // Act
        emailService.init();

        // Assert
        Boolean isEnabled = (Boolean) ReflectionTestUtils.getField(emailService, "isEnabled");
        assertThat(isEnabled).isTrue();

        // Verificamos que se creó una instancia del cliente (aunque sea la real,
        // validamos que no sea nula).
        Object client = ReflectionTestUtils.getField(emailService, "resendClient");
        assertThat(client).isNotNull();
    }

    @Test
    @DisplayName("init() debe deshabilitar el servicio si falta la API Key")
    void shouldDisableServiceWhenApiKeyIsMissing() {
        // Arrange: API Key nula o vacía
        injectProperties("", "no-reply@test.com");

        // Act
        emailService.init();

        // Assert
        Boolean isEnabled = (Boolean) ReflectionTestUtils.getField(emailService, "isEnabled");
        assertThat(isEnabled).isFalse();
    }

    // =========================================================================
    // 🚀 ESCENARIO 2: ENVÍO EXITOSO (MOCKING DE CADENA)
    // =========================================================================

    @Test
    @DisplayName("sendEmail() debe llamar a Resend API y retornar ID")
    void shouldSendEmailSuccessfully() throws ResendException {
        // Arrange
        String to = "user@test.com";
        String subject = "Bienvenido";
        String body = "<h1>Hola</h1>";
        String fakeId = "msg_123456789";

        // 1. Configuramos el servicio como 'habilitado' y le inyectamos el MOCK de Resend
        //    (Saltamos el init() real para usar nuestro mock controlado)
        ReflectionTestUtils.setField(emailService, "isEnabled", true);
        ReflectionTestUtils.setField(emailService, "resendClient", resendClientMock);
        ReflectionTestUtils.setField(emailService, "fromEmail", "no-reply@test.com");

        // 2. Simulamos la respuesta de Resend (CreateEmailResponse)
        CreateEmailResponse responseMock = mock(CreateEmailResponse.class);
        when(responseMock.getId()).thenReturn(fakeId);

        // 3. Simulamos la cadena: resend.emails().send(...)
        when(resendClientMock.emails()).thenReturn(emailsOperationMock);
        when(emailsOperationMock.send(any(CreateEmailOptions.class))).thenReturn(responseMock);

        // Act
        String resultId = emailService.sendEmail(to, subject, body);

        // Assert
        assertThat(resultId).isEqualTo(fakeId);

        // Verificamos que se construyó el objeto CreateEmailOptions correctamente
        ArgumentCaptor<CreateEmailOptions> optionsCaptor = ArgumentCaptor.forClass(CreateEmailOptions.class);
        verify(emailsOperationMock).send(optionsCaptor.capture());

        CreateEmailOptions sentOptions = optionsCaptor.getValue();
        assertThat(sentOptions.getTo()).contains(to);
        assertThat(sentOptions.getSubject()).isEqualTo(subject);
        assertThat(sentOptions.getHtml()).isEqualTo(body);
        assertThat(sentOptions.getFrom()).isEqualTo("no-reply@test.com");
    }

    // =========================================================================
    // ⚠️ ESCENARIO 3: VALIDACIONES Y ERRORES
    // =========================================================================

    @Test
    @DisplayName("sendEmail() debe lanzar IllegalStateException si el servicio está deshabilitado")
    void shouldThrowExceptionIfServiceDisabled() {
        // Arrange
        ReflectionTestUtils.setField(emailService, "isEnabled", false);

        // Act & Assert
        assertThatThrownBy(() -> emailService.sendEmail("a@b.com", "S", "B"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Servicio de Email no disponible");
    }

    @Test
    @DisplayName("sendEmail() debe envolver ResendException en RuntimeException")
    void shouldWrapResendException() throws ResendException {
        // Arrange
        ReflectionTestUtils.setField(emailService, "isEnabled", true);
        ReflectionTestUtils.setField(emailService, "resendClient", resendClientMock);

        // Simulamos error de la API (ej. límite excedido)
        when(resendClientMock.emails()).thenReturn(emailsOperationMock);
        when(emailsOperationMock.send(any()))
                .thenThrow(new ResendException("Rate limit exceeded"));

        // Act & Assert
        assertThatThrownBy(() -> emailService.sendEmail("a@b.com", "S", "B"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Fallo en proveedor de email: Rate limit exceeded");
    }

    // =========================================================================
    // 🛠️ HELPER
    // =========================================================================

    private void injectProperties(String apiKey, String from) {
        ReflectionTestUtils.setField(emailService, "resendApiKey", apiKey);
        ReflectionTestUtils.setField(emailService, "fromEmail", from);
    }
}
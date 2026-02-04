package com.quhealthy.notification_service.service.integration;

import com.twilio.Twilio;
import com.twilio.exception.ApiException;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.rest.api.v2010.account.MessageCreator;
import com.twilio.type.PhoneNumber;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SmsServiceTest {

    private SmsService smsService;

    @BeforeEach
    void setUp() {
        smsService = new SmsService();
    }

    @Test
    @DisplayName("init() debe habilitar el servicio si las credenciales existen")
    void shouldEnableServiceWhenConfigIsPresent() {
        injectProperties("AC123", "token123", "+1555000");

        try (MockedStatic<Twilio> twilioMock = mockStatic(Twilio.class)) {
            smsService.init();

            Boolean isEnabled = (Boolean) ReflectionTestUtils.getField(smsService, "isEnabled");
            assertThat(isEnabled).isTrue();

            twilioMock.verify(() -> Twilio.init("AC123", "token123"));
        }
    }

    @Test
    @DisplayName("init() debe deshabilitar el servicio si faltan credenciales")
    void shouldDisableServiceWhenConfigIsMissing() {
        injectProperties("", null, "");
        smsService.init();

        Boolean isEnabled = (Boolean) ReflectionTestUtils.getField(smsService, "isEnabled");
        assertThat(isEnabled).isFalse();
    }

    @Test
    @DisplayName("sendSms() debe lanzar error si el servicio no está habilitado")
    void shouldThrowExceptionIfServiceDisabled() {
        injectProperties(null, null, null);
        smsService.init();

        assertThatThrownBy(() -> smsService.sendSms("+123", "Hola"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Servicio de SMS no disponible");
    }

    @Test
    @DisplayName("sendSms() debe llamar a la API de Twilio y retornar SID")
    void shouldSendSmsSuccessfully() {
        injectProperties("AC_TEST", "TOKEN_TEST", "+19999999");
        ReflectionTestUtils.setField(smsService, "isEnabled", true);

        // Mocks
        Message mockMessage = mock(Message.class);
        when(mockMessage.getSid()).thenReturn("SM_FAKE_SID_123");

        MessageCreator mockCreator = mock(MessageCreator.class);
        when(mockCreator.create()).thenReturn(mockMessage);

        try (MockedStatic<Message> messageStaticMock = mockStatic(Message.class)) {
            messageStaticMock.when(() ->
                    Message.creator(any(PhoneNumber.class), any(PhoneNumber.class), anyString())
            ).thenReturn(mockCreator);

            // Act
            String resultSid = smsService.sendSms("+15551234567", "Hola Mundo");

            // Assert
            assertThat(resultSid).isEqualTo("SM_FAKE_SID_123");
        }
    }

    @Test
    @DisplayName("sendSms() debe lanzar IllegalArgumentException si el teléfono es vacío")
    void shouldThrowExceptionIfPhoneIsEmpty() {
        ReflectionTestUtils.setField(smsService, "isEnabled", true);

        assertThatThrownBy(() -> smsService.sendSms("", "Hola"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("sendSms() debe envolver errores de la API de Twilio en RuntimeException")
    void shouldWrapTwilioExceptions() {
        injectProperties("AC", "TK", "+1");
        ReflectionTestUtils.setField(smsService, "isEnabled", true);

        // ✅ ARREGLADO: Mock completo de la cadena Creator -> create()
        MessageCreator creatorMock = mock(MessageCreator.class);
        // Cuando se llame a .create(), simulamos el error de Twilio
        when(creatorMock.create()).thenThrow(new ApiException("Invalid Number"));

        try (MockedStatic<Message> messageStaticMock = mockStatic(Message.class)) {
            // Cuando se llame al estático creator(...), devolvemos el mockCreator válido
            messageStaticMock.when(() ->
                    Message.creator(any(PhoneNumber.class), any(PhoneNumber.class), anyString())
            ).thenReturn(creatorMock); // 👈 AQUÍ estaba el error antes, devolvía null implícitamente

            // Act & Assert
            assertThatThrownBy(() -> smsService.sendSms("+123", "Hola"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Twilio Error")
                    .hasMessageContaining("Invalid Number");
        }
    }

    private void injectProperties(String sid, String token, String from) {
        ReflectionTestUtils.setField(smsService, "accountSid", sid);
        ReflectionTestUtils.setField(smsService, "authToken", token);
        ReflectionTestUtils.setField(smsService, "fromNumber", from);
    }
}
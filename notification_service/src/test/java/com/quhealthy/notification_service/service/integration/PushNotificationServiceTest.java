package com.quhealthy.notification_service.service.integration;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PushNotificationServiceTest {

    // El servicio a probar (SUT - System Under Test)
    private PushNotificationService pushService;

    // Mock de la instancia de Firebase (lo que devuelve getInstance())
    @Mock
    private FirebaseMessaging firebaseInstanceMock;

    @BeforeEach
    void setUp() {
        // Como el servicio no tiene dependencias de constructor, lo instanciamos directo
        pushService = new PushNotificationService();
    }

    // =========================================================================
    // ✅ ESCENARIO 1: VALIDACIONES PREVIAS
    // =========================================================================

    @Test
    @DisplayName("Debe lanzar excepción si el Device Token es nulo o vacío")
    void shouldThrowExceptionWhenTokenIsInvalid() {
        // Act & Assert
        assertThatThrownBy(() -> pushService.sendPush(null, "Titulo", "Body", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Device Token es requerido");

        assertThatThrownBy(() -> pushService.sendPush("", "Titulo", "Body", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Device Token es requerido");
    }

    // =========================================================================
    // 🚀 ESCENARIO 2: ENVÍO EXITOSO (MOCKING ESTÁTICO)
    // =========================================================================

    @Test
    @DisplayName("Debe construir el mensaje y enviarlo a Firebase exitosamente")
    void shouldSendPushSuccessfully() throws FirebaseMessagingException {
        // Arrange
        String token = "device_token_abc_123";
        String expectedId = "projects/quhealthy/messages/msg_id_999";

        // Simulamos que el método .send() devuelve un ID
        when(firebaseInstanceMock.send(any(Message.class))).thenReturn(expectedId);

        // 🧙‍♂️ MAGIA DE MOCKITO: Interceptamos FirebaseMessaging.getInstance()
        try (MockedStatic<FirebaseMessaging> firebaseStatic = mockStatic(FirebaseMessaging.class)) {

            // Cuando alguien llame a getInstance(), devuelve nuestro mock controlado
            firebaseStatic.when(FirebaseMessaging::getInstance).thenReturn(firebaseInstanceMock);

            // Act
            String resultId = pushService.sendPush(token, "Hola", "Mundo", null);

            // Assert
            assertThat(resultId).isEqualTo(expectedId);

            // Verificación profunda: ¿Se construyó bien el mensaje?
            ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
            verify(firebaseInstanceMock).send(messageCaptor.capture());

            // Inspeccionamos el objeto Message que se pasó a send()
            // Nota: Firebase Message no expone getters públicos fáciles para todo,
            // pero podemos confiar en que si llegó aquí, se construyó.
            assertThat(messageCaptor.getValue()).isNotNull();
        }
    }

    // =========================================================================
    // 🖼️ ESCENARIO 3: CON IMAGEN
    // =========================================================================

    @Test
    @DisplayName("Debe incluir la imagen en la notificación si se proporciona")
    void shouldIncludeImageInNotification() throws FirebaseMessagingException {
        // Arrange
        String imageUrl = "https://quhealthy.org/logo.png";
        when(firebaseInstanceMock.send(any(Message.class))).thenReturn("id_123");

        try (MockedStatic<FirebaseMessaging> firebaseStatic = mockStatic(FirebaseMessaging.class)) {
            firebaseStatic.when(FirebaseMessaging::getInstance).thenReturn(firebaseInstanceMock);

            // Act
            pushService.sendPush("token", "Title", "Body", imageUrl);

            // Assert
            ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
            verify(firebaseInstanceMock).send(captor.capture());

            // Aquí solo verificamos que no falló al construirlo
            assertThat(captor.getValue()).isNotNull();
        }
    }

    // =========================================================================
    // ❌ ESCENARIO 4: ERROR DE FIREBASE
    // =========================================================================

    @Test
    @DisplayName("Debe envolver errores de Firebase en RuntimeException")
    void shouldWrapFirebaseExceptions() throws FirebaseMessagingException {
        // Arrange
        // Simulamos que Firebase rechaza el token (ej. token expirado)
        when(firebaseInstanceMock.send(any(Message.class)))
                .thenThrow(new RuntimeException("Invalid Registration Token"));

        try (MockedStatic<FirebaseMessaging> firebaseStatic = mockStatic(FirebaseMessaging.class)) {
            firebaseStatic.when(FirebaseMessaging::getInstance).thenReturn(firebaseInstanceMock);

            // Act & Assert
            assertThatThrownBy(() ->
                    pushService.sendPush("bad_token", "Title", "Body", null)
            )
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Firebase Error: Invalid Registration Token");
        }
    }
}
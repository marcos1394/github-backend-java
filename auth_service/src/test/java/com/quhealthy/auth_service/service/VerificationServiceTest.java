package com.quhealthy.auth_service.service;

import com.quhealthy.auth_service.dto.request.*;
import com.quhealthy.auth_service.dto.response.MessageResponse;
import com.quhealthy.auth_service.event.UserEvent;
import com.quhealthy.auth_service.event.UserEventPublisher; // ⚠️ CORRECCIÓN: Importamos la Interfaz
import com.quhealthy.auth_service.model.Consumer;
import com.quhealthy.auth_service.repository.ConsumerRepository;
import com.quhealthy.auth_service.repository.ProviderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VerificationServiceTest {

    @Mock private ConsumerRepository consumerRepository;
    @Mock private ProviderRepository providerRepository;
    @Mock private PasswordEncoder passwordEncoder;

    // ⚠️ CORRECCIÓN: Mockeamos la Interfaz 'UserEventPublisher', no la implementación.
    @Mock private UserEventPublisher eventPublisher;

    @InjectMocks
    private VerificationService verificationService;

    // ========================================================================
    // ✅ TEST: VERIFICACIÓN DE EMAIL
    // ========================================================================

    @Test
    @DisplayName("VerifyEmail: Debe verificar usuario correctamente si el token es válido")
    void verifyEmail_ShouldSuccess_WhenTokenIsValid() {
        // Arrange
        String token = "valid-uuid-token";
        Consumer consumer = Consumer.builder()
                .email("test@mail.com")
                .isEmailVerified(false)
                .emailVerificationToken(token)
                .build();

        when(consumerRepository.findByEmailVerificationToken(token)).thenReturn(Optional.of(consumer));

        // Usamos lenient() porque esta línea NO se ejecutará
        lenient().when(providerRepository.findByEmailVerificationToken(token)).thenReturn(Optional.empty());

        // Act
        MessageResponse response = verificationService.verifyEmail(new VerifyEmailRequest(token));

        // Assert
        assertNotNull(response);
        assertTrue(consumer.isEmailVerified());
        assertNull(consumer.getEmailVerificationToken());

        verify(consumerRepository).save(consumer);
        verify(eventPublisher).publish(any(UserEvent.class));
    }

    @Test
    @DisplayName("VerifyEmail: Debe fallar si el token no existe")
    void verifyEmail_ShouldThrow_WhenTokenNotFound() {
        String token = "invalid-token";
        when(consumerRepository.findByEmailVerificationToken(anyString())).thenReturn(Optional.empty());
        when(providerRepository.findByEmailVerificationToken(anyString())).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> verificationService.verifyEmail(new VerifyEmailRequest(token)));
    }

    // ========================================================================
    // 📱 TEST: VERIFICACIÓN DE TELÉFONO (OTP)
    // ========================================================================

    @Test
    @DisplayName("VerifyPhone: Debe verificar teléfono si el OTP es correcto y no ha expirado")
    void verifyPhone_ShouldSuccess_WhenCodeIsValid() {
        // Arrange
        String email = "phone@test.com";
        String code = "123456";

        Consumer consumer = Consumer.builder()
                .email(email)
                .isPhoneVerified(false)
                .phoneVerificationToken(code)
                .phoneVerificationExpires(LocalDateTime.now().plusMinutes(5))
                .build();

        when(consumerRepository.findByEmail(email)).thenReturn(Optional.of(consumer));

        // Act
        verificationService.verifyPhone(new VerifyPhoneRequest(code, email));

        // Assert
        assertTrue(consumer.isPhoneVerified());
        assertNull(consumer.getPhoneVerificationToken());
        verify(consumerRepository).save(consumer);
    }

    @Test
    @DisplayName("VerifyPhone: Debe fallar si el código expiró")
    void verifyPhone_ShouldThrow_WhenCodeExpired() {
        String email = "expired@test.com";
        Consumer consumer = Consumer.builder()
                .email(email)
                .phoneVerificationToken("123456")
                .phoneVerificationExpires(LocalDateTime.now().minusMinutes(1))
                .build();

        when(consumerRepository.findByEmail(email)).thenReturn(Optional.of(consumer));

        Exception ex = assertThrows(IllegalArgumentException.class,
                () -> verificationService.verifyPhone(new VerifyPhoneRequest("123456", email)));
        assertEquals("El código ha expirado. Solicita uno nuevo.", ex.getMessage());
    }

    // ========================================================================
    // 🔄 TEST: REENVÍO DE CÓDIGOS
    // ========================================================================

    @Test
    @DisplayName("ResendVerification: Debe generar nuevo token y evento para EMAIL")
    void resendVerification_ShouldSendNewEmailToken() {
        String email = "resend@test.com";
        Consumer consumer = Consumer.builder().email(email).isEmailVerified(false).build();
        when(consumerRepository.findByEmail(email)).thenReturn(Optional.of(consumer));

        verificationService.resendVerification(new ResendVerificationRequest(email, "EMAIL"));

        assertNotNull(consumer.getEmailVerificationToken());
        verify(eventPublisher).publish(any(UserEvent.class));
    }

    // ========================================================================
    // 🔑 TEST: FORGOT PASSWORD
    // ========================================================================

    @Test
    @DisplayName("ForgotPassword: Debe generar Selector/Verifier y guardar hash")
    void forgotPassword_ShouldGenerateSelectorAndVerifier() {
        String email = "forgot@test.com";
        Consumer consumer = Consumer.builder().email(email).build();
        when(consumerRepository.findByEmail(email)).thenReturn(Optional.of(consumer));
        when(passwordEncoder.encode(anyString())).thenReturn("hashed_verifier");

        verificationService.forgotPassword(new ForgotPasswordRequest(email));

        assertNotNull(consumer.getResetSelector());
        assertEquals("hashed_verifier", consumer.getResetVerifierHash());
        verify(eventPublisher).publish(any(UserEvent.class));
    }

    // ========================================================================
    // 🔐 TEST: RESET PASSWORD
    // ========================================================================

    @Test
    @DisplayName("ResetPassword: Debe funcionar si Selector existe y Verifier coincide con Hash")
    void resetPassword_ShouldSuccess_WhenSelectorAndVerifierMatch() {
        String selector = "valid-selector";
        String verifier = "secret-verifier";
        String compositeToken = selector + ":" + verifier;
        String newPassword = "NewPassword123!";

        Consumer consumer = Consumer.builder()
                .id(1L)
                .resetSelector(selector)
                .resetVerifierHash("hashed_secret_verifier")
                .resetTokenExpiresAt(LocalDateTime.now().plusHours(1))
                .build();

        when(consumerRepository.findByResetSelector(selector)).thenReturn(Optional.of(consumer));
        lenient().when(providerRepository.findByResetSelector(selector)).thenReturn(Optional.empty());

        when(passwordEncoder.matches(verifier, "hashed_secret_verifier")).thenReturn(true);
        when(passwordEncoder.encode(newPassword)).thenReturn("encoded_new_password");

        verificationService.resetPassword(new ResetPasswordRequest(compositeToken, newPassword));

        verify(consumerRepository).save(consumer);
        assertNull(consumer.getResetSelector());
    }

    @Test
    @DisplayName("ResetPassword: Debe fallar si el token tiene formato inválido")
    void resetPassword_ShouldThrow_WhenTokenFormatInvalid() {
        ResetPasswordRequest request = new ResetPasswordRequest("token-sin-dos-puntos", "Pass123");
        assertThrows(IllegalArgumentException.class,
                () -> verificationService.resetPassword(request));
    }

    @Test
    @DisplayName("ResetPassword: Debe fallar si el Verifier no coincide con el Hash")
    void resetPassword_ShouldThrow_WhenVerifierDoesNotMatch() {
        String selector = "valid-selector";
        String wrongVerifier = "hacker-input";
        String compositeToken = selector + ":" + wrongVerifier;

        Consumer consumer = Consumer.builder()
                .resetSelector(selector)
                .resetVerifierHash("real_hash")
                .resetTokenExpiresAt(LocalDateTime.now().plusHours(1))
                .build();

        when(consumerRepository.findByResetSelector(selector)).thenReturn(Optional.of(consumer));
        when(passwordEncoder.matches(wrongVerifier, "real_hash")).thenReturn(false);

        Exception ex = assertThrows(IllegalArgumentException.class,
                () -> verificationService.resetPassword(new ResetPasswordRequest(compositeToken, "Pass")));

        assertEquals("Token de seguridad inválido.", ex.getMessage());
    }
}
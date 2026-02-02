package com.quhealthy.auth_service.service;

import com.quhealthy.auth_service.dto.request.ForgotPasswordRequest;
import com.quhealthy.auth_service.dto.request.ResendVerificationRequest;
import com.quhealthy.auth_service.dto.request.ResetPasswordRequest;
import com.quhealthy.auth_service.dto.request.VerifyEmailRequest;
import com.quhealthy.auth_service.dto.request.VerifyPhoneRequest;
import com.quhealthy.auth_service.dto.response.MessageResponse;
import com.quhealthy.auth_service.event.UserEvent;
import com.quhealthy.auth_service.event.UserEventPublisher; // ✅ IMPORT DE LA INTERFAZ
import com.quhealthy.auth_service.model.BaseUser;
import com.quhealthy.auth_service.model.Consumer;
import com.quhealthy.auth_service.model.Provider;
import com.quhealthy.auth_service.repository.ConsumerRepository;
import com.quhealthy.auth_service.repository.ProviderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VerificationService {

    private final ConsumerRepository consumerRepository;
    private final ProviderRepository providerRepository;
    private final PasswordEncoder passwordEncoder;

    // ✅ INYECCIÓN DE LA INTERFAZ (Desacoplado de Pub/Sub)
    private final UserEventPublisher eventPublisher;

    // Generador criptográficamente seguro para el Verifier
    private final SecureRandom secureRandom = new SecureRandom();

    // ========================================================================
    // ✅ 1. VERIFICACIÓN DE EMAIL
    // ========================================================================

    @Transactional
    public MessageResponse verifyEmail(VerifyEmailRequest request) {
        String token = request.getToken();
        log.info("Procesando verificación de email con token: {}", token);

        Optional<Consumer> consumerOpt = consumerRepository.findByEmailVerificationToken(token);
        if (consumerOpt.isPresent()) {
            return confirmEmailVerification(consumerOpt.get());
        }

        Optional<Provider> providerOpt = providerRepository.findByEmailVerificationToken(token);
        if (providerOpt.isPresent()) {
            return confirmEmailVerification(providerOpt.get());
        }

        throw new IllegalArgumentException("El token de verificación es inválido o ha expirado.");
    }

    // ========================================================================
    // 📱 2. VERIFICACIÓN DE TELÉFONO (OTP)
    // ========================================================================

    @Transactional
    public MessageResponse verifyPhone(VerifyPhoneRequest request) {
        log.info("Procesando verificación de teléfono para: {}", request.getIdentifier());

        BaseUser user = findUserByEmail(request.getIdentifier())
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));

        if (user.getPhoneVerificationToken() == null ||
                !user.getPhoneVerificationToken().equals(request.getCode())) {
            throw new IllegalArgumentException("El código de verificación es incorrecto.");
        }

        if (user.getPhoneVerificationExpires() == null ||
                user.getPhoneVerificationExpires().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("El código ha expirado. Solicita uno nuevo.");
        }

        user.setPhoneVerified(true);
        user.setPhoneVerificationToken(null);
        user.setPhoneVerificationExpires(null);

        saveUser(user);

        publishEvent(user, "PHONE_VERIFIED", null);

        return new MessageResponse("Teléfono verificado exitosamente.");
    }

    // ========================================================================
    // 🔄 3. REENVÍO DE CÓDIGOS
    // ========================================================================

    @Transactional
    public MessageResponse resendVerification(ResendVerificationRequest request) {
        log.info("Solicitud de reenvío de verificación ({}) para: {}", request.getType(), request.getEmail());

        BaseUser user = findUserByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado con email: " + request.getEmail()));

        if ("EMAIL".equalsIgnoreCase(request.getType())) {
            if (user.isEmailVerified()) {
                return new MessageResponse("El correo electrónico ya está verificado.");
            }
            String newToken = UUID.randomUUID().toString();
            user.setEmailVerificationToken(newToken);
            user.setEmailVerificationExpires(LocalDateTime.now().plusHours(24));

            saveUser(user);
            publishEvent(user, "VERIFICATION_EMAIL_RESENT", newToken);

            return new MessageResponse("Se ha enviado un nuevo enlace de verificación a su correo.");

        } else if ("SMS".equalsIgnoreCase(request.getType())) {
            if (user.isPhoneVerified()) {
                return new MessageResponse("El teléfono ya está verificado.");
            }
            String otp = String.format("%06d", new Random().nextInt(999999));
            user.setPhoneVerificationToken(otp);
            user.setPhoneVerificationExpires(LocalDateTime.now().plusMinutes(10));

            saveUser(user);
            publishEvent(user, "VERIFICATION_SMS_RESENT", otp);

            return new MessageResponse("Se ha enviado un nuevo código SMS a su teléfono.");
        }

        throw new IllegalArgumentException("Tipo de verificación no soportado: " + request.getType());
    }

    // ========================================================================
    // 🔑 4. RECUPERACIÓN DE CONTRASEÑA (SELECTOR / VERIFIER PATTERN)
    // ========================================================================

    @Transactional
    public MessageResponse forgotPassword(ForgotPasswordRequest request) {
        Optional<BaseUser> userOpt = findUserByEmail(request.getEmail());

        if (userOpt.isEmpty()) {
            log.warn("ForgotPassword solicitado para email inexistente: {}", request.getEmail());
            return new MessageResponse("Si el correo existe en nuestro sistema, recibirás instrucciones.");
        }

        BaseUser user = userOpt.get();

        // 1. Generar Selector (Búsqueda pública)
        String selector = UUID.randomUUID().toString();

        // 2. Generar Verifier (Secreto)
        byte[] verifierBytes = new byte[32];
        secureRandom.nextBytes(verifierBytes);
        String verifier = Base64.getUrlEncoder().withoutPadding().encodeToString(verifierBytes);

        // 3. Guardar en BD: Selector en claro, Verifier HASHEADO
        user.setResetSelector(selector);
        user.setResetVerifierHash(passwordEncoder.encode(verifier));
        user.setResetTokenExpiresAt(LocalDateTime.now().plusHours(1));

        saveUser(user);

        // 4. Construir Token Compuesto para el email: "selector:verifier"
        String compositeToken = selector + ":" + verifier;

        // 5. Publicar evento (NotificationService construye el link)
        publishEvent(user, "PASSWORD_RESET_REQUESTED", compositeToken);

        return new MessageResponse("Si el correo existe en nuestro sistema, recibirás instrucciones.");
    }

    // ========================================================================
    // 🔐 5. RESETEO DE CONTRASEÑA (VALIDACIÓN SELECTOR / VERIFIER)
    // ========================================================================

    @Transactional
    public MessageResponse resetPassword(ResetPasswordRequest request) {
        // 1. Descomponer el token compuesto (selector:verifier)
        String[] parts = request.getToken().split(":");
        if (parts.length != 2) {
            throw new IllegalArgumentException("El token de restablecimiento tiene un formato inválido.");
        }

        String selector = parts[0];
        String verifier = parts[1];

        // 2. Buscar usuario SOLO por Selector (rápido y seguro)
        BaseUser user = findUserByResetSelector(selector)
                .orElseThrow(() -> new IllegalArgumentException("El enlace es inválido o ha expirado."));

        // 3. Validar expiración
        if (user.getResetTokenExpiresAt() == null ||
                user.getResetTokenExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("El enlace de restablecimiento ha expirado. Solicita uno nuevo.");
        }

        // 4. Validar Verifier contra el Hash almacenado (evita timing attacks)
        if (!passwordEncoder.matches(verifier, user.getResetVerifierHash())) {
            log.warn("Intento fallido de reset password. Verifier no coincide para selector: {}", selector);
            throw new IllegalArgumentException("Token de seguridad inválido.");
        }

        // 5. Actualizar contraseña
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));

        // 6. Limpiar campos de seguridad
        user.setResetSelector(null);
        user.setResetVerifierHash(null);
        user.setResetTokenExpiresAt(null);

        saveUser(user);

        // 7. Notificar éxito
        publishEvent(user, "PASSWORD_RESET_SUCCESS", null);

        return new MessageResponse("Tu contraseña ha sido restablecida exitosamente.");
    }

    // ========================================================================
    // 🛠️ MÉTODOS PRIVADOS (HELPER METHODS)
    // ========================================================================

    private MessageResponse confirmEmailVerification(BaseUser user) {
        if (user.isEmailVerified()) {
            return new MessageResponse("El correo ya estaba verificado anteriormente.");
        }
        user.setEmailVerified(true);
        user.setEmailVerificationToken(null);
        user.setEmailVerificationExpires(null);
        saveUser(user);

        publishEvent(user, "EMAIL_VERIFIED", null);
        return new MessageResponse("¡Correo verificado exitosamente! Ya puedes iniciar sesión.");
    }

    private Optional<BaseUser> findUserByEmail(String email) {
        Optional<Consumer> c = consumerRepository.findByEmail(email);
        if (c.isPresent()) return Optional.of(c.get());

        Optional<Provider> p = providerRepository.findByEmail(email);
        if (p.isPresent()) return Optional.of(p.get());

        return Optional.empty();
    }

    /**
     * Busca usuario usando el Selector (parte pública del token).
     */
    private Optional<BaseUser> findUserByResetSelector(String selector) {
        Optional<Consumer> c = consumerRepository.findByResetSelector(selector);
        if (c.isPresent()) return Optional.of(c.get());

        Optional<Provider> p = providerRepository.findByResetSelector(selector);
        if (p.isPresent()) return Optional.of(p.get());

        return Optional.empty();
    }

    private void saveUser(BaseUser user) {
        if (user instanceof Consumer) {
            consumerRepository.save((Consumer) user);
        } else if (user instanceof Provider) {
            providerRepository.save((Provider) user);
        } else {
            throw new IllegalStateException("Tipo de usuario desconocido.");
        }
    }

    private void publishEvent(BaseUser user, String eventType, String tokenOrCode) {
        Map<String, Object> payload = new HashMap<>();

        if (tokenOrCode != null) {
            if (eventType.contains("SMS") || eventType.contains("PHONE")) {
                payload.put("otpCode", tokenOrCode);
            } else {
                // Aquí enviamos el token compuesto "selector:verifier"
                payload.put("token", tokenOrCode);
            }
        }

        payload.put("name", user.getFirstName());

        UserEvent event = UserEvent.builder()
                .eventType(eventType)
                .userId(user.getId())
                .email(user.getEmail())
                .role(user instanceof Consumer ? "CONSUMER" : "PROVIDER")
                .payload(payload)
                .timestamp(LocalDateTime.now())
                .build();

        // ✅ LLAMADA A TRAVÉS DE LA INTERFAZ
        eventPublisher.publish(event);
    }
}
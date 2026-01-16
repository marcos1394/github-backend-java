package com.quhealthy.auth_service.service.impl;

import com.quhealthy.auth_service.model.Notification;
import com.quhealthy.auth_service.model.enums.Role;
import com.quhealthy.auth_service.repository.NotificationRepository;
import com.quhealthy.auth_service.service.NotificationService;

// --- IMPORTS DE RESEND (SEGÚN DOCUMENTACIÓN OFICIAL) ---
import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;

// --- IMPORTS DE TWILIO ---
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate messagingTemplate;

    // Credenciales
    @Value("${resend.api.key}")
    private String resendApiKey;

    @Value("${twilio.account.sid}")
    private String twilioSid;

    @Value("${twilio.auth.token}")
    private String twilioToken;

    @Value("${twilio.phone.number}")
    private String twilioPhoneNumber;

    // ========================================================================
    // 1. IN-APP (BD + WebSocket)
    // ========================================================================
    @Transactional
    public Notification createInAppNotification(Long userId, Role userRole, String message, String link) {
        try {
            Notification notification = new Notification();
            notification.setUserId(userId);
            notification.setUserRole(userRole);
            notification.setMessage(message);
            notification.setLink(link);
            notification.setRead(false);

            Notification saved = notificationRepository.save(notification);
            
            // Emitir WebSocket
            messagingTemplate.convertAndSendToUser(
                String.valueOf(userId),
                "/queue/notifications",
                saved
            );

            return saved;
        } catch (Exception e) {
            log.error("❌ Error In-App Notification: {}", e.getMessage());
            return null;
        }
    }

    // Métodos auxiliares de lectura
    public List<Notification> getUserNotifications(Long userId, Role userRole) {
        return notificationRepository.findByUserIdAndUserRoleOrderByCreatedAtDesc(userId, userRole);
    }

    public void markAllAsRead(Long userId, Role userRole) {
        notificationRepository.markAllAsRead(userId, userRole);
    }

    // ========================================================================
    // 2. EMAIL (RESEND - IMPLEMENTACIÓN OFICIAL)
    // ========================================================================
    @Override
    public void sendVerificationEmail(String to, String name, String link) {
        // Validación básica
        if (resendApiKey == null || resendApiKey.isBlank()) {
            log.warn("⚠️ Resend API Key no configurada.");
            return;
        }

        // 1. Instanciar cliente como en la doc
        Resend resend = new Resend(resendApiKey);

        // 2. Crear HTML
        String htmlContent = "<strong>Hola " + name + "</strong>, <br>Por favor verifica tu cuenta aquí: <a href='" + link + "'>Verificar</a>";

        // 3. Configurar parámetros (Builder Pattern)
        CreateEmailOptions params = CreateEmailOptions.builder()
                .from("QuHealthy <onboarding@resend.dev>") // TODO: Cambiar por tu dominio verificado
                .to(to)
                .subject("Verifica tu cuenta")
                .html(htmlContent)
                .build();

        // 4. Enviar con manejo de errores específico de Resend
        try {
            CreateEmailResponse data = resend.emails().send(params);
            log.info("📧 Email enviado exitosamente. ID: {}", data.getId());
        } catch (ResendException e) {
            log.error("❌ Error enviando email con Resend: {}", e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            log.error("❌ Error general en envío de email: {}", e.getMessage());
        }
    }

    // ========================================================================
    // 3. SMS (TWILIO)
    // ========================================================================
    @Override
    public void sendVerificationSms(String phone, String token) {
        if (twilioSid == null || twilioToken == null) return;

        try {
            Twilio.init(twilioSid, twilioToken);
            Message.creator(
                new PhoneNumber(phone),
                new PhoneNumber(twilioPhoneNumber),
                "Tu código es: " + token
            ).create();
            log.info("📱 SMS enviado a {}", phone);
        } catch (Exception e) {
            log.error("❌ Error Twilio: {}", e.getMessage());
        }
    }
}
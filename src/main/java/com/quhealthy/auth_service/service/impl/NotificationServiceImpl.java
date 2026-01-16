package com.quhealthy.auth_service.service.impl;

import com.quhealthy.auth_service.model.Notification;
import com.quhealthy.auth_service.model.enums.Role;
import com.quhealthy.auth_service.repository.NotificationRepository;
import com.quhealthy.auth_service.service.NotificationService;

// --- RESEND V3 ---
import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;

// --- TWILIO ---
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;

// --- SPRING & THYMELEAF ---
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate messagingTemplate;
    
    // 🔥 INYECCIÓN DEL MOTOR DE PLANTILLAS
    private final TemplateEngine templateEngine;

    // --- Credenciales (Desde application.properties) ---
    @Value("${resend.api.key}")
    private String resendApiKey;

    @Value("${twilio.account.sid}")
    private String twilioSid;

    @Value("${twilio.auth.token}")
    private String twilioToken;

    @Value("${twilio.phone.number}")
    private String twilioPhoneNumber;

    // ========================================================================
    // 1. IN-APP (BD + WebSocket) - Sin cambios, ya funcionaba bien
    // ========================================================================
    @Override
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
            
            // Emitir WebSocket a /user/{id}/queue/notifications
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

    @Override
    public List<Notification> getUserNotifications(Long userId, Role userRole) {
        return notificationRepository.findByUserIdAndUserRoleOrderByCreatedAtDesc(userId, userRole);
    }

    @Override
    public void markAllAsRead(Long userId, Role userRole) {
        notificationRepository.markAllAsRead(userId, userRole);
    }

    // ========================================================================
    // 2. EMAILS PROFESIONALES (Thymeleaf + Resend)
    // ========================================================================

    /**
     * Método HELPER privado para reutilizar la lógica de envío.
     * Combina las variables (Context) con la plantilla HTML y envía por Resend.
     */
    private void sendHtmlEmail(String to, String subject, String templateName, Context context) {
        // Validación de API Key
        if (resendApiKey == null || resendApiKey.isBlank()) {
            log.warn("⚠️ Resend API Key no configurada. Email '{}' no enviado.", templateName);
            return;
        }

        try {
            // 1. Procesar la plantilla HTML con las variables
            String htmlContent = templateEngine.process(templateName, context);

            // 2. Configurar Resend
            Resend resend = new Resend(resendApiKey);
            CreateEmailOptions params = CreateEmailOptions.builder()
                    .from("QuHealthy <onboarding@resend.dev>") // TODO: Configura tu dominio real en Prod
                    .to(to)
                    .subject(subject)
                    .html(htmlContent)
                    .build();

            // 3. Enviar
            resend.emails().send(params);
            log.info("📧 Email '{}' enviado exitosamente a {}", templateName, to);

        } catch (ResendException e) {
            log.error("❌ Error Resend ({}) : {}", templateName, e.getMessage());
        } catch (Exception e) {
            log.error("❌ Error general email ({}) : {}", templateName, e.getMessage());
        }
    }

    // --- Implementación de los métodos públicos usando el Helper ---

    @Override
    public void sendVerificationEmail(String to, String name, String link) {
        Context context = new Context();
        context.setVariable("name", name);
        context.setVariable("link", link);
        // Usa la plantilla: templates/email-verification.html
        sendHtmlEmail(to, "Verifica tu cuenta - QuHealthy", "email-verification", context);
    }

    @Override
    public void sendPasswordResetRequest(String to, String link) {
        Context context = new Context();
        context.setVariable("link", link);
        // Usa la plantilla: templates/password-reset-request.html
        sendHtmlEmail(to, "Restablecer Contraseña", "password-reset-request", context);
    }

    @Override
    public void sendPasswordChangedAlert(String to, String name, String time, String device) {
        Context context = new Context();
        context.setVariable("name", name);
        context.setVariable("time", time);
        context.setVariable("date", LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy")));
        context.setVariable("supportLink", "https://quhealthy.com/support"); // Link estático o variable
        // Usa la plantilla: templates/password-changed.html
        sendHtmlEmail(to, "Alerta de Seguridad: Contraseña modificada", "password-changed", context);
    }

    @Override
    public void sendLoginAlert(String to, String name, String device, String location, String ip) {
        Context context = new Context();
        context.setVariable("name", name);
        context.setVariable("device", device);
        context.setVariable("location", location);
        context.setVariable("time", LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
        context.setVariable("ip", ip);
        // Usa la plantilla: templates/login-alert.html
        sendHtmlEmail(to, "Nuevo inicio de sesión detectado", "login-alert", context);
    }

    @Override
    public void sendOtpCode(String to, String code) {
        Context context = new Context();
        context.setVariable("code", code);
        // Usa la plantilla: templates/otp-code.html
        sendHtmlEmail(to, "Tu código de seguridad", "otp-code", context);
    }

    // ========================================================================
    // 3. SMS (TWILIO)
    // ========================================================================
    @Override
    public void sendVerificationSms(String phone, String token) {
        if (twilioSid == null || twilioToken == null) {
            log.warn("⚠️ Twilio credenciales no configuradas.");
            return;
        }

        try {
            Twilio.init(twilioSid, twilioToken);
            Message.creator(
                new PhoneNumber(phone),
                new PhoneNumber(twilioPhoneNumber),
                "QuHealthy: Tu código de verificación es " + token
            ).create();
            log.info("📱 SMS enviado a {}", phone);
        } catch (Exception e) {
            log.error("❌ Error Twilio: {}", e.getMessage());
        }
    }
}
package com.quhealthy.auth_service.service.impl;

import com.quhealthy.auth_service.model.Notification;
import com.quhealthy.auth_service.model.enums.Role;
import com.quhealthy.auth_service.repository.NotificationRepository;
import com.quhealthy.auth_service.service.NotificationService;
import com.quhealthy.auth_service.service.notification.ResendService; // 👈 Inyección del especialista Email
import com.quhealthy.auth_service.service.notification.TwilioService; // 👈 Inyección del especialista SMS

// --- SPRING & THYMELEAF ---
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    // Repositorios y Motores
    private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final TemplateEngine templateEngine;
    
    // 🔥 SERVICIOS DELEGADOS (Los Especialistas)
    private final ResendService resendService;
    private final TwilioService twilioService;

    // ========================================================================
    // 1. IN-APP (BD + WebSocket)
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
            
            // Push por WebSocket en tiempo real
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
    // 2. EMAILS (Orquestación: Plantilla -> ResendService)
    // ========================================================================
    
    /**
     * Helper privado: Genera el HTML y se lo pasa al especialista.
     */
    private void prepareAndSendEmail(String to, String subject, String templateName, Context context) {
        try {
            // 1. NotificationService genera el contenido (Negocio)
            String htmlContent = templateEngine.process(templateName, context);
            
            // 2. ResendService entrega el mensaje (Infraestructura)
            resendService.sendHtmlEmail(to, subject, htmlContent);
            
        } catch (Exception e) {
            log.error("❌ Error procesando plantilla '{}': {}", templateName, e.getMessage());
        }
    }

    @Override
    public void sendVerificationEmail(String to, String name, String link) {
        Context context = new Context();
        context.setVariable("name", name);
        context.setVariable("link", link);
        prepareAndSendEmail(to, "Verifica tu cuenta - QuHealthy", "email-verification", context);
    }

    @Override
    public void sendPasswordResetRequest(String to, String link) {
        Context context = new Context();
        context.setVariable("link", link);
        prepareAndSendEmail(to, "Restablecer Contraseña", "password-reset-request", context);
    }

    @Override
    public void sendPasswordChangedAlert(String to, String name, String time, String device) {
        Context context = new Context();
        context.setVariable("name", name);
        context.setVariable("time", time);
        context.setVariable("date", LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy")));
        context.setVariable("supportLink", "https://quhealthy.com/support");
        prepareAndSendEmail(to, "Alerta de Seguridad", "password-changed", context);
    }

    @Override
    public void sendLoginAlert(String to, String name, String device, String location, String ip) {
        Context context = new Context();
        context.setVariable("name", name);
        context.setVariable("device", device);
        context.setVariable("location", location);
        context.setVariable("time", LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
        context.setVariable("ip", ip);
        prepareAndSendEmail(to, "Nuevo inicio de sesión", "login-alert", context);
    }

    @Override
    public void sendOtpCode(String to, String code) {
        Context context = new Context();
        context.setVariable("code", code);
        prepareAndSendEmail(to, "Tu código de seguridad", "otp-code", context);
    }

    // ========================================================================
    // 3. SMS (Orquestación: -> TwilioService)
    // ========================================================================
    @Override
    public void sendVerificationSms(String phone, String token) {
        twilioService.sendVerificationSms(phone, token);
    }
}
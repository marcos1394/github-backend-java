package com.quhealthy.notification_service.service;

import com.quhealthy.notification_service.dto.NotificationResponse;
import com.quhealthy.notification_service.dto.UnreadCountResponse;
import com.quhealthy.notification_service.model.Notification;
import com.quhealthy.notification_service.model.enums.NotificationType;
import com.quhealthy.notification_service.model.enums.TargetRole;
import com.quhealthy.notification_service.repository.NotificationRepository;
// ✅ VERIFICA ESTOS IMPORTS DE RESEND
import com.resend.Resend;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final TemplateEngine templateEngine;

    @Value("${resend.api.key}")
    private String resendApiKey;

    @Value("${resend.from.email:noreply@quhealthy.com}")
    private String fromEmail;

    @Value("${twilio.account.sid}")
    private String twilioSid;

    @Value("${twilio.auth.token}")
    private String twilioToken;

    @Value("${twilio.phone.number}")
    private String twilioFromNumber;

    private Resend resendClient;

    @PostConstruct
    public void init() {
        try {
            // Inicializar Resend
            if (resendApiKey != null && !resendApiKey.isBlank()) {
                this.resendClient = new Resend(resendApiKey);
            }
            // Inicializar Twilio
            if (twilioSid != null && !twilioSid.isBlank()) {
                Twilio.init(twilioSid, twilioToken);
            }
            log.info("📡 Notification Service inicializado.");
        } catch (Exception e) {
            log.warn("⚠️ No se pudieron inicializar los clientes externos (Resend/Twilio): {}", e.getMessage());
        }
    }

    @Transactional
    public void createAndSend(Long userId, TargetRole role, NotificationType type, 
                              String title, String message, String actionLink, 
                              String recipientContact, List<String> channels) {
        
        log.info("🔔 Procesando notificación para Usuario {} [{}]: {}", userId, role, title);

        // 1. In-App
        if (channels.contains("IN_APP") || channels.isEmpty()) {
            Notification notification = Notification.builder()
                    .userId(userId)
                    .targetRole(role)
                    .type(type)
                    .title(title)
                    .message(message)
                    .actionLink(actionLink)
                    .isRead(false) // @Builder.Default manejará esto, pero ser explícito no daña
                    .build();
            notificationRepository.save(notification);
        }

        // 2. Email
        if (channels.contains("EMAIL") && recipientContact != null && recipientContact.contains("@")) {
            sendEmailSafely(recipientContact, title, message);
        }

        // 3. SMS/WhatsApp
        if ((channels.contains("SMS") || channels.contains("WHATSAPP")) && recipientContact != null) {
            sendSmsSafely(recipientContact, message, channels.contains("WHATSAPP"));
        }
    }
    
    private void sendEmailSafely(String to, String subject, String body) {
        if (resendClient == null) return;
        
        try {
            // Usamos CreateEmailOptions del SDK de Resend
            CreateEmailOptions params = CreateEmailOptions.builder()
                    .from(fromEmail)
                    .to(to)
                    .subject(subject)
                    .html("<p>" + body + "</p>")
                    .build();

            CreateEmailResponse data = resendClient.emails().send(params);
            log.info("📧 Email enviado a {}: ID {}", to, data.getId());

        } catch (Exception e) {
            log.error("❌ Error enviando email: {}", e.getMessage());
        }
    }

    private void sendSmsSafely(String to, String body, boolean isWhatsapp) {
        try {
            String finalTo = to;
            String finalFrom = twilioFromNumber;

            if (isWhatsapp) {
                finalTo = "whatsapp:" + to;
                finalFrom = "whatsapp:" + twilioFromNumber; 
            }

            Message.creator(
                    new PhoneNumber(finalTo),
                    new PhoneNumber(finalFrom),
                    body
            ).create();

        } catch (Exception e) {
            log.error("❌ Error enviando SMS/WA: {}", e.getMessage());
        }
    }

    // --- Métodos de Lectura ---

    @Transactional(readOnly = true)
    public Page<NotificationResponse> getUserNotifications(Long userId, String roleStr, Pageable pageable) {
        TargetRole role = TargetRole.valueOf(roleStr);
        return notificationRepository.findByUserIdAndTargetRole(userId, role, pageable)
                .map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse getUnreadCount(Long userId, String roleStr) {
        TargetRole role = TargetRole.valueOf(roleStr);
        long count = notificationRepository.countByUserIdAndTargetRoleAndIsReadFalse(userId, role);
        return new UnreadCountResponse(count);
    }

    @Transactional
    public void markAllAsRead(Long userId, String roleStr) {
        TargetRole role = TargetRole.valueOf(roleStr);
        notificationRepository.markAllAsRead(userId, role);
    }
    
    @Transactional
    public void markOneAsRead(Long notificationId, Long userId) {
        notificationRepository.findById(notificationId).ifPresent(n -> {
            if (n.getUserId().equals(userId)) {
                n.setRead(true);
                notificationRepository.save(n);
            }
        });
    }

    private NotificationResponse mapToResponse(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .title(n.getTitle())
                .message(n.getMessage())
                .type(n.getType())
                .isRead(n.isRead())
                .actionLink(n.getActionLink())
                .createdAt(n.getCreatedAt())
                .build();
    }
}
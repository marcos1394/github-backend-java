package com.quhealthy.notification_service.service;

import com.quhealthy.notification_service.dto.NotificationResponse;
import com.quhealthy.notification_service.dto.UnreadCountResponse;
import com.quhealthy.notification_service.model.Notification;
import com.quhealthy.notification_service.model.NotificationLog;
import com.quhealthy.notification_service.model.enums.NotificationChannel;
import com.quhealthy.notification_service.model.enums.NotificationStatus;
import com.quhealthy.notification_service.model.enums.NotificationType;
import com.quhealthy.notification_service.model.enums.TargetRole;
import com.quhealthy.notification_service.repository.NotificationLogRepository;
import com.quhealthy.notification_service.repository.NotificationRepository;
import com.quhealthy.notification_service.service.content.TemplateService;
import com.quhealthy.notification_service.service.integration.EmailService;
import com.quhealthy.notification_service.service.integration.PushNotificationService;
import com.quhealthy.notification_service.service.integration.SmsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationLogRepository logRepository;

    // Integraciones
    private final EmailService emailService;
    private final SmsService smsService;
    private final PushNotificationService pushService;
    private final TemplateService templateService;

    /**
     * MÉTODO MAESTRO: Recibe un evento y decide por qué canales notificar.
     */
    @Transactional
    public void createAndSend(
            Long userId,
            TargetRole role,
            NotificationType type,
            String title,
            String simpleMessage, // Mensaje corto para SMS/Push/InApp
            String actionLink,
            String recipientContact, // Email o Teléfono principal
            List<String> channels, // ["EMAIL", "SMS", "IN_APP"]
            Map<String, Object> templateVariables, // Datos para el HTML
            String templateName // Nombre del archivo HTML (opcional)
    ) {

        // 1. Siempre guardar IN_APP si está en la lista o por defecto
        if (channels.contains("IN_APP")) {
            Notification inApp = Notification.builder()
                    .userId(userId)
                    .targetRole(role)
                    .type(type)
                    .title(title)
                    .message(simpleMessage)
                    .actionLink(actionLink)
                    .isRead(false)
                    .build();
            notificationRepository.save(inApp);
        }

        // 2. Procesar canales externos
        for (String channelStr : channels) {
            try {
                NotificationChannel channel = NotificationChannel.valueOf(channelStr);

                // Ignoramos IN_APP aquí porque ya lo guardamos arriba
                if (channel == NotificationChannel.IN_APP) continue;

                processExternalChannel(channel, userId, role, recipientContact, title, simpleMessage, templateName, templateVariables);

            } catch (IllegalArgumentException e) {
                log.warn("Canal desconocido ignorado: {}", channelStr);
            }
        }
    }

    private void processExternalChannel(NotificationChannel channel, Long userId, TargetRole role, String contact, String title, String body, String templateName, Map<String, Object> vars) {

        // Crear Log Inicial
        NotificationLog logEntry = NotificationLog.builder()
                .userId(userId)
                .targetRole(role)
                .channel(channel)
                .recipient(contact)
                .subject(title)
                .status(NotificationStatus.PENDING)
                .build();

        logEntry = logRepository.save(logEntry);

        try {
            String providerId = null;

            switch (channel) {
                case EMAIL:
                    // Si hay template, lo generamos. Si no, usamos el texto plano en HTML simple.
                    String htmlContent = (templateName != null)
                            ? templateService.generateContent(templateName, vars)
                            : "<p>" + body + "</p>";

                    providerId = emailService.sendEmail(contact, title, htmlContent);
                    break;

                case SMS:
                    providerId = smsService.sendSms(contact, body);
                    break;

                case PUSH_NOTIFICATION:
                    // Para Push, asumimos que 'contact' es el Device Token
                    providerId = pushService.sendPush(contact, title, body, null);
                    break;

                default:
                    throw new UnsupportedOperationException("Canal no implementado: " + channel);
            }

            logEntry.setStatus(NotificationStatus.SENT);
            logEntry.setProviderId(providerId);

        } catch (Exception e) {
            logEntry.setStatus(NotificationStatus.FAILED);
            logEntry.setErrorMessage(e.getMessage());
            log.error("Fallo envío {} a {}: {}", channel, contact, e.getMessage());
        }

        logRepository.save(logEntry);
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> getUserNotifications(Long userId, TargetRole role, Pageable pageable) {
        // 1. Obtener Entidades de BD
        Page<Notification> notifications = notificationRepository.findByUserIdAndTargetRole(userId, role, pageable);

        // 2. Mapear Entidad -> DTO
        return notifications.map(this::mapToDto);
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse getUnreadCount(Long userId, TargetRole role) {
        long count = notificationRepository.countByUserIdAndTargetRoleAndIsReadFalse(userId, role);
        return UnreadCountResponse.builder().unreadCount(count).build();
    }

    @Transactional
    public void markOneAsRead(Long notificationId, Long userId, TargetRole role) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Notificación no encontrada"));

        // Seguridad: Verificar que la notificación pertenece al usuario y su rol actual
        if (!notification.getUserId().equals(userId) || !notification.getTargetRole().equals(role)) {
            throw new SecurityException("No tienes permiso para modificar esta notificación");
        }

        notification.setRead(true);
        notificationRepository.save(notification);
    }

    @Transactional
    public void markAllAsRead(Long userId, TargetRole role) {
        notificationRepository.markAllAsRead(userId, role);
    }

    // Helper de Mapeo
    private NotificationResponse mapToDto(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .title(n.getTitle())
                .message(n.getMessage())
                .type(n.getType())
                .isRead(n.isRead())
                .actionLink(n.getActionLink())
                .metadata(n.getMetadata())
                .createdAt(n.getCreatedAt())
                .build();
    }

}
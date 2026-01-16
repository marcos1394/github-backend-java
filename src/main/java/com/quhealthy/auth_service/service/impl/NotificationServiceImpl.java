package com.quhealthy.auth_service.service.impl;

import com.quhealthy.auth_service.model.Notification;
import com.quhealthy.auth_service.model.enums.Role;
import com.quhealthy.auth_service.repository.NotificationRepository;
import com.quhealthy.auth_service.service.NotificationService;
import com.resend.Resend;
import com.resend.services.emails.model.CreateEmailOptions;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;

    // Inyectamos las credenciales desde application.properties
    @Value("${resend.api.key}")
    private String resendApiKey;

    @Value("${twilio.account.sid}")
    private String twilioSid;

    @Value("${twilio.auth.token}")
    private String twilioToken;

    @Value("${twilio.phone.number}")
    private String twilioPhoneNumber;

    // --- 1. EMAIL (Resend) ---
    @Override
    public void sendVerificationEmail(String to, String name, String link) {
        try {
            Resend resend = new Resend(resendApiKey);

            // Plantilla HTML simple (puedes mejorarla luego con la que me pasaste)
            String htmlContent = "<h1>Hola " + name + ", bienvenido a QuHealthy!</h1>" +
                    "<p>Por favor verifica tu cuenta haciendo click aquí:</p>" +
                    "<a href='" + link + "' style='padding:10px; background:#7c3aed; color:white; border-radius:5px;'>Verificar Email</a>";

            CreateEmailOptions params = CreateEmailOptions.builder()
                    .from("QuHealthy <onboarding@resend.dev>") // Cambia esto por tu dominio verificado en Resend
                    .to(to)
                    .subject("Verifica tu cuenta en QuHealthy")
                    .html(htmlContent)
                    .build();

            resend.emails().send(params);
            log.info("✅ Email enviado a {}", to);

        } catch (Exception e) {
            log.error("❌ Error enviando email con Resend: {}", e.getMessage());
            // No lanzamos excepción para no romper el flujo de registro
        }
    }

    // --- 2. SMS (Twilio) ---
    @Override
    public void sendVerificationSms(String phone, String token) {
        try {
            Twilio.init(twilioSid, twilioToken);
            
            Message.creator(
                new PhoneNumber(phone),
                new PhoneNumber(twilioPhoneNumber),
                "Tu código de verificación QuHealthy es: " + token
            ).create();
            
            log.info("✅ SMS enviado a {}", phone);

        } catch (Exception e) {
            log.error("❌ Error enviando SMS con Twilio: {}", e.getMessage());
        }
    }

    // --- 3. IN-APP (Base de Datos + "Socket.IO") ---
    // Este método reemplaza tu 'createNotification' de Node.js
    @Transactional
    public Notification createInAppNotification(Long userId, Role userRole, String message, String link) {
        try {
            // 1. Guardar en BD
            Notification notification = new Notification();
            notification.setUserId(userId);
            notification.setUserRole(userRole);
            notification.setMessage(message);
            notification.setLink(link);
            notification.setRead(false);
            
            Notification savedNotification = notificationRepository.save(notification);
            
            // 2. Emitir en tiempo real
            sendRealTimeNotification(userId, savedNotification);
            
            log.info("✅ Notificación In-App creada para usuario {}", userId);
            return savedNotification;

        } catch (Exception e) {
            log.error("❌ Error creando notificación In-App: {}", e.getMessage());
            return null;
        }
    }

    private void sendRealTimeNotification(Long userId, Notification notification) {
        // TODO: Implementar Spring WebSockets (STOMP) aquí en el siguiente paso.
        // Esto reemplaza a: io.to(userId).emit(...)
        // simpMessagingTemplate.convertAndSendToUser(userId.toString(), "/queue/notifications", notification);
    }
}
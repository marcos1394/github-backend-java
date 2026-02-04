package com.quhealthy.notification_service.service.integration;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
public class PushNotificationService {

    /**
     * Envía una notificación Push a través de FCM (Firebase Cloud Messaging).
     *
     * @param deviceToken Token único del dispositivo móvil.
     * @param title Título de la alerta.
     * @param body Cuerpo del mensaje.
     * @param image URL de imagen (Opcional).
     * @return ID del mensaje generado por Firebase.
     */
    public String sendPush(String deviceToken, String title, String body, String image) {
        if (!StringUtils.hasText(deviceToken)) {
            log.warn("⚠️ [PUSH] Intento de envío sin Device Token. Omitiendo.");
            throw new IllegalArgumentException("Device Token es requerido para Push Notifications");
        }

        try {
            // Construimos la notificación visual
            Notification.Builder notificationBuilder = Notification.builder()
                    .setTitle(title)
                    .setBody(body);

            if (StringUtils.hasText(image)) {
                notificationBuilder.setImage(image);
            }

            // Construimos el mensaje de transporte
            Message message = Message.builder()
                    .setToken(deviceToken)
                    .setNotification(notificationBuilder.build())
                    // Data opcional oculta para lógica en background app
                    // .putData("click_action", "FLUTTER_NOTIFICATION_CLICK")
                    .build();

            String response = FirebaseMessaging.getInstance().send(message);
            log.info("✅ [PUSH] Enviado correctamente a {}. ID: {}", deviceToken.substring(0, 5) + "...", response);
            return response;

        } catch (Exception e) {
            log.error("❌ [PUSH] Error enviando a {}: {}", deviceToken, e.getMessage());
            throw new RuntimeException("Firebase Error: " + e.getMessage(), e);
        }
    }
}
package com.quhealthy.notification_service.model.enums;

public enum NotificationStatus {
    PENDING,   // Creado, listo para procesar
    SENT,      // Enviado a Twilio/Resend exitosamente
    DELIVERED, // (Opcional) Confirmado por webhook que llegó al usuario
    FAILED,    // Error al enviar (Ej: Credenciales mal)
    BOUNCED    // Enviado, pero rebotó (Email inválido)
}
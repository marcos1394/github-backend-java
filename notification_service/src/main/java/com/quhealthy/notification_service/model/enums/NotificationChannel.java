package com.quhealthy.notification_service.model.enums;

public enum NotificationChannel {
    EMAIL,
    SMS,
    WHATSAPP,
    PUSH_NOTIFICATION, // Firebase / OneSignal
    IN_APP // Solo informativo, generalmente se guarda en la otra tabla
}
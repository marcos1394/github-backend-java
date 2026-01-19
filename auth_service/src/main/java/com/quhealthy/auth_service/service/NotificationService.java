package com.quhealthy.auth_service.service;

import com.quhealthy.auth_service.model.Notification;
import com.quhealthy.auth_service.model.enums.Role;

import java.util.List;

public interface NotificationService {

    // --- Notificaciones In-App ---
    Notification createInAppNotification(Long userId, Role userRole, String message, String link);
    List<Notification> getUserNotifications(Long userId, Role userRole);
    void markAllAsRead(Long userId, Role userRole);

    // --- Emails Transaccionales (Ahora soportamos todos los casos) ---
    void sendVerificationEmail(String to, String name, String link);
    void sendPasswordResetRequest(String to, String link);
    void sendPasswordChangedAlert(String to, String name, String time, String device); // Alerta de seguridad
    void sendLoginAlert(String to, String name, String device, String location, String ip);
    void sendOtpCode(String to, String code); // Para 2FA por correo

    // --- SMS ---
    void sendVerificationSms(String phone, String token);
}
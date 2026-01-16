package com.quhealthy.auth_service.service;

public interface NotificationService {
    void sendVerificationEmail(String to, String name, String link);
    void sendVerificationSms(String phone, String token);
}
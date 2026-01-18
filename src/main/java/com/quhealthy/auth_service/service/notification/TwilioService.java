package com.quhealthy.auth_service.service.notification;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class TwilioService {

    @Value("${twilio.account.sid}")
    private String accountSid;

    @Value("${twilio.auth.token}")
    private String authToken;

    @Value("${twilio.phone.number}")
    private String fromPhoneNumber;

    @PostConstruct
    public void init() {
        try {
            // Inicializamos Twilio una sola vez al arrancar la app
            if (accountSid != null && authToken != null) {
                Twilio.init(accountSid, authToken);
                log.info("📞 [TwilioService] Inicializado correctamente.");
            } else {
                log.warn("⚠️ [TwilioService] Faltan credenciales en application.properties");
            }
        } catch (Exception e) {
            log.error("❌ [TwilioService] Error al inicializar: {}", e.getMessage());
        }
    }

    public void sendVerificationSms(String to, String code) {
        if (fromPhoneNumber == null) {
            log.error("❌ Twilio Phone Number no configurado.");
            return;
        }

        try {
            Message.creator(
                new PhoneNumber(to),
                new PhoneNumber(fromPhoneNumber),
                "Tu código de verificación de QuHealthy es: " + code
            ).create();
            
            log.info("📱 SMS enviado a {}", to);
        } catch (Exception e) {
            log.error("❌ Error enviando SMS a {}: {}", to, e.getMessage());
            // No lanzamos error para no detener el flujo principal del usuario
        }
    }
}
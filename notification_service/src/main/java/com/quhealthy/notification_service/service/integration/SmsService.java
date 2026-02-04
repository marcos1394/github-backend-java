package com.quhealthy.notification_service.service.integration;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
public class SmsService {

    @Value("${twilio.account.sid:}")
    private String accountSid;

    @Value("${twilio.auth.token:}")
    private String authToken;

    @Value("${twilio.phone.number:}")
    private String fromNumber;

    private boolean isEnabled = false;

    @PostConstruct
    public void init() {
        if (!StringUtils.hasText(accountSid) || !StringUtils.hasText(authToken) || !StringUtils.hasText(fromNumber)) {
            log.warn("⚠️ [SMS] Credenciales de Twilio incompletas. Servicio SMS DESHABILITADO.");
            this.isEnabled = false;
            return;
        }

        try {
            Twilio.init(accountSid, authToken);
            this.isEnabled = true;
            log.info("✅ [SMS] Twilio inicializado correctamente. Sender: {}", fromNumber);
        } catch (Exception e) {
            log.error("❌ [SMS] Error inicializando Twilio: {}", e.getMessage());
            this.isEnabled = false;
        }
    }

    public String sendSms(String toPhoneNumber, String body) {
        if (!isEnabled) {
            log.error("❌ [SMS] Intento de envío fallido: Servicio no habilitado.");
            throw new IllegalStateException("Servicio de SMS no disponible");
        }

        if (!StringUtils.hasText(toPhoneNumber)) {
            throw new IllegalArgumentException("El número de destino es requerido");
        }

        try {
            Message message = Message.creator(
                    new PhoneNumber(toPhoneNumber),
                    new PhoneNumber(fromNumber),
                    body
            ).create();

            log.info("✅ [SMS] Enviado a {}. SID: {}", toPhoneNumber, message.getSid());
            return message.getSid();

        } catch (Exception e) {
            log.error("❌ [SMS] Error enviando a {}: {}", toPhoneNumber, e.getMessage());
            throw new RuntimeException("Twilio Error: " + e.getMessage(), e);
        }
    }
}
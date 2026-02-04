package com.quhealthy.notification_service.service.integration;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class EmailService {

    @Value("${resend.api.key:}") // Default vacío para evitar error al arrancar si falta
    private String resendApiKey;

    @Value("${resend.from.email:noreply@quhealthy.org}")
    private String fromEmail;

    private Resend resendClient;
    private boolean isEnabled = false;

    @PostConstruct
    public void init() {
        if (resendApiKey == null || resendApiKey.isBlank()) {
            log.warn("⚠️ [EMAIL] API Key no configurada. El servicio de correo está DESHABILITADO.");
            this.isEnabled = false;
            return;
        }
        try {
            this.resendClient = new Resend(resendApiKey);
            this.isEnabled = true;
            log.info("✅ [EMAIL] Servicio Resend inicializado correctamente.");
        } catch (Exception e) {
            log.error("❌ [EMAIL] Error inicializando cliente Resend: {}", e.getMessage());
            this.isEnabled = false;
        }
    }

    public String sendEmail(String to, String subject, String htmlContent) {
        if (!isEnabled || resendClient == null) {
            log.error("❌ [EMAIL] Intento de envío fallido: Servicio no habilitado o mal configurado.");
            throw new IllegalStateException("Servicio de Email no disponible");
        }

        log.info("📧 Enviando email a: [{}] | Subject: [{}]", to, subject);

        try {
            CreateEmailOptions params = CreateEmailOptions.builder()
                    .from(fromEmail)
                    .to(to)
                    .subject(subject)
                    .html(htmlContent)
                    .build();

            CreateEmailResponse data = resendClient.emails().send(params);

            log.info("✅ Email enviado. ID: {}", data.getId());
            return data.getId();

        } catch (ResendException e) {
            log.error("❌ [EMAIL] Error de API Resend al enviar a {}: {}", to, e.getMessage());
            throw new RuntimeException("Fallo en proveedor de email: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("❌ [EMAIL] Error inesperado enviando email a {}: {}", to, e.getMessage());
            throw new RuntimeException("Error inesperado de email", e);
        }
    }
}
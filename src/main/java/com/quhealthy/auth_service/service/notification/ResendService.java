package com.quhealthy.auth_service.service.notification;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ResendService {

    @Value("${resend.api.key}")
    private String apiKey;

    // Configurable en application.properties, con valor por defecto para desarrollo
    @Value("${resend.from.email}") // AHORA (Sin default, obliga a usar el secreto)
    private String fromEmail;

    private Resend resendClient;

    @PostConstruct
    public void init() {
        if (apiKey != null && !apiKey.isBlank()) {
            this.resendClient = new Resend(apiKey);
            log.info("📧 [ResendService] Cliente inicializado correctamente.");
        } else {
            log.warn("⚠️ [ResendService] API Key no encontrada. El envío de correos fallará.");
        }
    }

    /**
     * Envía un correo HTML usando Resend.
     * @param to Destinatario
     * @param subject Asunto
     * @param htmlContent Contenido HTML ya procesado
     */
    public void sendHtmlEmail(String to, String subject, String htmlContent) {
        if (resendClient == null) {
            log.error("❌ [ResendService] Error: Cliente no inicializado (Falta API Key).");
            return;
        }

        try {
            CreateEmailOptions params = CreateEmailOptions.builder()
                    .from(fromEmail)
                    .to(to)
                    .subject(subject)
                    .html(htmlContent)
                    .build();

            resendClient.emails().send(params);
            log.info("📧 Email enviado exitosamente a: {}", to);

        } catch (ResendException e) {
            log.error("❌ [ResendService] Error de API Resend: {}", e.getMessage());
        } catch (Exception e) {
            log.error("❌ [ResendService] Error general enviando email a {}: {}", to, e.getMessage());
        }
    }
}
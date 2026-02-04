package com.quhealthy.notification_service.service.content;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateService {

    private final TemplateEngine templateEngine;

    /**
     * Procesa un archivo HTML y reemplaza variables.
     * @param templateName Nombre del archivo en resources/templates (sin .html a veces, depende de config, pero mejor sin extensión)
     * @param variables Mapa con valores (ej: "name" -> "Juan"). Puede ser null.
     * @return El HTML renderizado final.
     */
    public String generateContent(String templateName, Map<String, Object> variables) {
        if (!StringUtils.hasText(templateName)) {
            throw new IllegalArgumentException("El nombre del template no puede estar vacío");
        }

        try {
            Context context = new Context();

            // Protección contra variables nulas
            if (variables != null) {
                context.setVariables(variables);
            } else {
                context.setVariables(Collections.emptyMap());
            }

            return templateEngine.process(templateName, context);

        } catch (Exception e) {
            log.error("❌ Error renderizando template '{}': {}", templateName, e.getMessage());
            // En caso de error de template, podríamos retornar un fallback o relanzar
            throw new RuntimeException("Error procesando template de email", e);
        }
    }
}
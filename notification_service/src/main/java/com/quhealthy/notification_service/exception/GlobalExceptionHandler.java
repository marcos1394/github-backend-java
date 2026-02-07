package com.quhealthy.notification_service.exception;

import com.quhealthy.notification_service.dto.ErrorResponse;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.MailSendException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ========================================================================
    // 📧 1. MANEJO DE ERRORES DE CORREO (Spring Mail Specifics)
    // ========================================================================

    /**
     * 🔐 Error de Autenticación (500).
     * Significa que nuestras credenciales (SMTP/SendGrid) en el application.yml están mal.
     * Es un error crítico de configuración del servidor.
     */
    @ExceptionHandler(MailAuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleMailAuth(MailAuthenticationException ex, HttpServletRequest request) {
        log.error("🚨 ERROR CRÍTICO DE CONFIGURACIÓN DE CORREO: Autenticación fallida. Revise credenciales SMTP.", ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "MAIL_CONFIG_ERROR", "Error interno en el servicio de correo. Contacte al administrador.", request);
    }

    /**
     * 📝 Error de Parseo (400).
     * El correo electrónico del destinatario tiene un formato inválido o caracteres ilegales.
     * Culpa del cliente por enviar un email mal formado.
     */
    @ExceptionHandler(MailParseException.class)
    public ResponseEntity<ErrorResponse> handleMailParse(MailParseException ex, HttpServletRequest request) {
        log.warn("Formato de correo inválido: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, "INVALID_EMAIL_FORMAT", "La dirección de correo electrónico tiene un formato inválido.", request);
    }

    /**
     * 🏗️ Error de Preparación (500).
     * Falló el motor de plantillas (Thymeleaf/FreeMarker) al renderizar el HTML.
     * Posiblemente faltan variables en el contexto del template.
     */
    @ExceptionHandler(MailPreparationException.class)
    public ResponseEntity<ErrorResponse> handleMailPrep(MailPreparationException ex, HttpServletRequest request) {
        log.error("Error renderizando plantilla de correo: ", ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "TEMPLATE_RENDER_ERROR", "Error al generar el contenido del correo.", request);
    }

    /**
     * 📤 Error de Envío (502 Bad Gateway).
     * Logramos conectar, pero el servidor SMTP rechazó el mensaje o hubo timeout.
     * Es un error "aguas arriba" (Upstream).
     */
    @ExceptionHandler(MailSendException.class)
    public ResponseEntity<ErrorResponse> handleMailSend(MailSendException ex, HttpServletRequest request) {
        log.error("Fallo en el envío del correo (Proveedor SMTP/API): ", ex);

        // A veces MailSendException agrupa varios errores, intentamos sacar el mensaje más claro
        String detail = "No se pudo entregar el mensaje al proveedor de correo.";
        if (ex.getMessage() != null && !ex.getMessage().isEmpty()) {
            // Limpiamos un poco el mensaje para no exponer stack traces crudos al usuario
            detail = "Error de comunicación con el servidor de correo.";
        }

        return buildResponse(HttpStatus.BAD_GATEWAY, "EMAIL_DELIVERY_FAILED", detail, request);
    }

    /**
     * 🧩 Error Genérico de Correo (500).
     * Catch-all para cualquier otra subclase de MailException no listada arriba.
     */
    @ExceptionHandler(MailException.class)
    public ResponseEntity<ErrorResponse> handleGeneralMail(MailException ex, HttpServletRequest request) {
        log.error("Error general de correo no clasificado: ", ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "MAIL_ERROR", "Ocurrió un error inesperado con el servicio de correo.", request);
    }

    // ========================================================================
    // 🛡️ 2. VALIDACIONES Y SEGURIDAD (Estándar)
    // ========================================================================

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(EntityNotFoundException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }

        log.warn("Error de validación en notificación: {}", errors);

        ErrorResponse response = ErrorResponse.builder()
                .code("VALIDATION_ERROR")
                .message("Datos de notificación inválidos")
                .timestamp(LocalDateTime.now())
                .path(request.getRequestURI())
                .errors(errors)
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler({SecurityException.class, AccessDeniedException.class})
    public ResponseEntity<ErrorResponse> handleSecurity(RuntimeException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "No tiene permisos para enviar esta notificación.", request);
    }

    // ========================================================================
    // 🌐 3. CATCH-ALL
    // ========================================================================

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex, HttpServletRequest request) {
        log.error("Error interno CRÍTICO en Notification Service: ", ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Error inesperado en el servicio de notificaciones.", request);
    }

    // ========================================================================
    // 🛠️ HELPER
    // ========================================================================

    private ResponseEntity<ErrorResponse> buildResponse(HttpStatus status, String code, String message, HttpServletRequest request) {
        ErrorResponse response = ErrorResponse.builder()
                .code(code)
                .message(message)
                .timestamp(LocalDateTime.now())
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.status(status).body(response);
    }
}
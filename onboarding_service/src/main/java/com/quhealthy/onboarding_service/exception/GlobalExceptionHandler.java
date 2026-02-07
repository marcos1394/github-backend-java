package com.quhealthy.onboarding_service.exception;

import com.quhealthy.onboarding_service.dto.response.ErrorResponse;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException; // ✅ NUEVO
import org.springframework.web.bind.MissingServletRequestParameterException; // ✅ NUEVO
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException; // ✅ NUEVO
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ========================================================================
    // 1. ERRORES DE PETICIÓN (CLIENTE - 400)
    // ========================================================================

    // Faltan parámetros (@RequestParam)
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParams(MissingServletRequestParameterException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, "MISSING_PARAMETER", "Falta el parámetro requerido: " + ex.getParameterName(), request);
    }

    // Faltan cabeceras (@RequestHeader) -> Este es el que fallaba en tu test
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeaders(MissingRequestHeaderException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, "MISSING_HEADER", "Falta la cabecera requerida: " + ex.getHeaderName(), request);
    }

    // Error de tipos (Ej: Enviar texto donde va un Enum o un Long)
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String name = ex.getName();
        String type = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown";
        Object value = ex.getValue();
        String message = String.format("El parámetro '%s' debería ser de tipo '%s', pero se recibió: '%s'", name, type, value);

        return buildResponse(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER_TYPE", message, request);
    }

    // Falta el archivo en multipart (@RequestPart)
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorResponse> handleMissingFile(MissingServletRequestPartException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, "MISSING_FILE", "Se requiere la parte del archivo: " + ex.getRequestPartName(), request);
    }

    // Errores de Validación de DTO (@Valid)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }
        log.warn("Error de validación: {}", errors);

        ErrorResponse response = ErrorResponse.builder()
                .code("VALIDATION_ERROR")
                .message("Datos de entrada inválidos")
                .timestamp(LocalDateTime.now())
                .path(request.getRequestURI())
                .errors(errors)
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    // Reglas de Negocio (Argumentos ilegales lanzados manualmente)
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<ErrorResponse> handleBusinessRules(RuntimeException ex, HttpServletRequest request) {
        log.warn("Regla de negocio violada: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, "BUSINESS_RULE_VIOLATION", ex.getMessage(), request);
    }

    // ========================================================================
    // 2. ERRORES DE ARCHIVOS Y STORAGE
    // ========================================================================

    // Archivo demasiado grande (413)
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxSize(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        log.warn("Intento de subir archivo demasiado grande");
        return buildResponse(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "El archivo excede el tamaño máximo permitido (10MB)", request);
    }

    // Error de IO (503)
    @ExceptionHandler(IOException.class)
    public ResponseEntity<ErrorResponse> handleIOError(IOException ex, HttpServletRequest request) {
        log.error("Error de IO (Storage/Network): ", ex);
        return buildResponse(HttpStatus.SERVICE_UNAVAILABLE, "STORAGE_ERROR", "Error al procesar el archivo. Intente más tarde.", request);
    }

    // ========================================================================
    // 3. ERRORES DE RECURSOS Y SEGURIDAD
    // ========================================================================

    // Recurso no encontrado (404)
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(EntityNotFoundException ex, HttpServletRequest request) {
        log.warn("Recurso no encontrado: {}", ex.getMessage());
        return buildResponse(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", ex.getMessage(), request);
    }

    // Seguridad (403)
    @ExceptionHandler({SecurityException.class, AccessDeniedException.class})
    public ResponseEntity<ErrorResponse> handleSecurity(RuntimeException ex, HttpServletRequest request) {
        log.error("Acceso denegado: {}", ex.getMessage());
        return buildResponse(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "No tienes permisos para realizar esta acción.", request);
    }

    // ========================================================================
    // 4. CATCH-ALL (500)
    // ========================================================================

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex, HttpServletRequest request) {
        log.error("Error interno NO controlado en Onboarding: ", ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Ha ocurrido un error inesperado. Contacte soporte.", request);
    }

    // Helper
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
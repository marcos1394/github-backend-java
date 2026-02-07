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
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 1. Recurso no encontrado (Perfil, Onboarding Status, Documento) -> 404
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(EntityNotFoundException ex, HttpServletRequest request) {
        log.warn("Recurso no encontrado: {}", ex.getMessage());
        return buildResponse(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", ex.getMessage(), request);
    }

    // 2. Errores de Validación de Archivos (Tamaño excedido) -> 413
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxSize(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        log.warn("Intento de subir archivo demasiado grande");
        return buildResponse(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "El archivo excede el tamaño máximo permitido (10MB)", request);
    }

    // 3. Falta el archivo en el request (No envían 'file') -> 400
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorResponse> handleMissingFile(MissingServletRequestPartException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, "MISSING_FILE", "Se requiere el parámetro: " + ex.getRequestPartName(), request);
    }

    // 4. Errores de Entrada/Salida (Storage, Lectura de bytes) -> 503
    @ExceptionHandler(IOException.class)
    public ResponseEntity<ErrorResponse> handleIOError(IOException ex, HttpServletRequest request) {
        log.error("Error de IO (Storage/Network): ", ex);
        return buildResponse(HttpStatus.SERVICE_UNAVAILABLE, "STORAGE_ERROR", "Error al procesar el archivo. Intente más tarde.", request);
    }

    // 5. Errores de Validación (@Valid en DTOs) -> 400
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

    // 6. Reglas de Negocio (Argumentos ilegales) -> 400
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<ErrorResponse> handleBusinessRules(RuntimeException ex, HttpServletRequest request) {
        log.warn("Regla de negocio violada: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, "BUSINESS_RULE_VIOLATION", ex.getMessage(), request);
    }

    // 7. Seguridad (Token inválido o sin permisos) -> 403
    @ExceptionHandler({SecurityException.class, AccessDeniedException.class})
    public ResponseEntity<ErrorResponse> handleSecurity(RuntimeException ex, HttpServletRequest request) {
        log.error("Acceso denegado: {}", ex.getMessage());
        return buildResponse(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "No tienes permisos para realizar esta acción o tu sesión expiró.", request);
    }

    // 8. Catch-All (Error inesperado) -> 500
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex, HttpServletRequest request) {
        log.error("Error interno NO controlado en Onboarding: ", ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Ha ocurrido un error inesperado. Contacte soporte.", request);
    }

    // Helper para construir la respuesta rápida
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
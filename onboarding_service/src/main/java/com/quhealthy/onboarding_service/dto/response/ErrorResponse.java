package com.quhealthy.onboarding_service.dto.response; // Ajustar package según el micro

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class ErrorResponse {
    private String code;
    private String message;
    private LocalDateTime timestamp;
    private String path;
    private Map<String, String> errors;
}
package com.quhealthy.payment_service.dto;

import com.quhealthy.payment_service.model.enums.PaymentGateway;
import com.quhealthy.payment_service.model.enums.SubscriptionStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class SubscriptionResponse {
    
    private UUID id; // Nuestro ID interno
    private String planId;
    private SubscriptionStatus status;
    private PaymentGateway gateway;
    
    private LocalDateTime currentPeriodStart;
    private LocalDateTime currentPeriodEnd; // "Tu plan vence el..."
    
    private boolean isActive; // Propiedad calculada para facilitar la UI del frontend
    
    private boolean cancelAtPeriodEnd; // ¿Se cancelará al final del mes?
}
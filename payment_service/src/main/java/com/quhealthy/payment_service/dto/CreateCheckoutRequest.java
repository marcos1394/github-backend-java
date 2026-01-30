package com.quhealthy.payment_service.dto;

import com.quhealthy.payment_service.model.enums.PaymentGateway; // Asegúrate que este import sea correcto
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateCheckoutRequest {
    
    // ✅ CAMBIO 1: Debe ser String para soportar IDs de Stripe ("price_Hj8...")
    @NotNull(message = "El Plan ID es obligatorio")
    private String planId; 

    @NotBlank(message = "La URL de éxito es obligatoria")
    private String successUrl;

    @NotBlank(message = "La URL de cancelación es obligatoria")
    private String cancelUrl;

    // ✅ CAMBIO 2: Debe ser Enum para que tu 'if (request.getGateway() == PaymentGateway.STRIPE)' funcione
    @NotNull(message = "El gateway es obligatorio")
    private PaymentGateway gateway; 
    
    // Campos opcionales para lógica nueva (si los necesitas), si no, ignóralos
    private Integer trialDays;
    private String email;
    private String name;
}
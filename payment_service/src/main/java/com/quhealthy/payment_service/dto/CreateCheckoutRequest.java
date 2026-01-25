package com.quhealthy.payment_service.dto;

import com.quhealthy.payment_service.model.enums.PaymentGateway;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateCheckoutRequest {

    @NotBlank(message = "El ID del plan es obligatorio (ej: price_123...)")
    private String planId; // El ID del precio en Stripe/MP

    @NotNull(message = "Debes seleccionar una pasarela de pago")
    private PaymentGateway gateway; // STRIPE o MERCADOPAGO

    // URLs para redirigir al usuario después de pagar en la pasarela
    @NotBlank(message = "La URL de éxito es obligatoria")
    private String successUrl; // ej: https://quhealthy.com/dashboard?payment=success

    @NotBlank(message = "La URL de cancelación es obligatoria")
    private String cancelUrl;  // ej: https://quhealthy.com/pricing
}
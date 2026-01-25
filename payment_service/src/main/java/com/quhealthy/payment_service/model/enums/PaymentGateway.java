package com.quhealthy.payment_service.model.enums;

public enum PaymentGateway {
    STRIPE,
    MERCADOPAGO,
    MANUAL, // Para dar acceso VIP o pruebas internas
    FREE    // Para el plan gratuito por defecto
}
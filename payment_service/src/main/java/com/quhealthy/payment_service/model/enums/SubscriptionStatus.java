package com.quhealthy.payment_service.model.enums;

public enum SubscriptionStatus {
    ACTIVE,         // Todo bien, pagado y funcionando
    PAST_DUE,       // Falló el pago, estamos reintentando (Gracia)
    CANCELED,
    PENDING,       // El usuario canceló o se agotaron los intentos de cobro
    UNPAID,         // Factura final no pagada
    TRIALING,       // Periodo de prueba gratis
    INCOMPLETE      // Se creó la intención pero no se completó el pago inicial
}
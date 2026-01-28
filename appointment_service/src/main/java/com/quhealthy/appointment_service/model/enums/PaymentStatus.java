package com.quhealthy.appointment_service.model.enums;

public enum PaymentStatus {
    PENDING,            // Aún no se paga (común en pagos en efectivo)
    AUTHORIZED,         // Retención en tarjeta (pero no cobrado aun)
    SETTLED,            // Cobrado exitosamente (Dinero en banco/mano)
    PARTIALLY_REFUNDED, // Se devolvió una parte (Reglas de cancelación)
    REFUNDED,           // Se devolvió todo
    FAILED,             // Tarjeta rechazada
    WAIVED              // Condonado (No se cobrará)
}
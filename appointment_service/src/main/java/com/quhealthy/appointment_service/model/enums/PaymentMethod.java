package com.quhealthy.appointment_service.model.enums;

public enum PaymentMethod {
    // --- Pagos Digitales (Plataforma) ---
    STRIPE,             // Tarjeta procesada por la App (Futuro)
    MERCADO_PAGO,       // Tarjeta/OXXO procesada por la App (Futuro)
    
    // --- Pagos Físicos (Consultorio) ---
    CASH,               // Efectivo en mano
    TERMINAL_CARD,      // Terminal bancaria externa del doctor (Clip, Izettle, Banco)
    BANK_TRANSFER,      // SPEI directo al doctor
    
    // --- Lógica de Negocio ---
    PACKAGE_REDEMPTION, // Canje de créditos pre-comprados
    INSURANCE,          // Pago vía Aseguradora (GMM)
    COURTESY            // 100% de descuento (Amigos/Familia/Garantía)
}
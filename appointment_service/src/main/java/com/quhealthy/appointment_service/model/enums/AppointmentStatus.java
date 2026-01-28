package com.quhealthy.appointment_service.model.enums;

public enum AppointmentStatus {
    PENDING_APPROVAL,    // (Opcional) Si el doctor debe aceptar manualmente la solicitud
    SCHEDULED,           // Confirmada en agenda (Espacio bloqueado)
    WAITING_ROOM,        // El paciente ya llegó a la clínica (Check-in)
    IN_PROGRESS,         // La consulta está ocurriendo en este momento
    COMPLETED,           // Finalizó exitosamente (Dispara review)
    
    // Variantes de Cancelación (Vitales para reembolso)
    CANCELED_BY_PATIENT, 
    CANCELED_BY_PROVIDER,
    
    // Variantes de Fallo
    NO_SHOW,             // Paciente no se presentó (Afecta reputación paciente)
    RESCHEDULED          // Se movió a otra fecha (Mantiene el historial)
}
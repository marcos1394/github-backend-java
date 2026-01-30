package com.quhealthy.payment_service.dto.pdf;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class PdfAppointmentReceiptDto {
    private String appointmentId;
    private LocalDateTime appointmentDate;
    
    // Doctor
    private String providerName;
    private String serviceName;
    
    // Paciente
    private String patientName;
    
    // Pago
    private BigDecimal amountPaid;
    private LocalDateTime paymentDate;
    private String paymentMethod; // "Tarjeta (Stripe)"
}
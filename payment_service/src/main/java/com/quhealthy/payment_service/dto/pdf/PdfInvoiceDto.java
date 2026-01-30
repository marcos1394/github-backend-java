package com.quhealthy.payment_service.dto.pdf;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class PdfInvoiceDto {
    private String transactionId;
    private LocalDateTime paymentDate;
    
    // Proveedor (Quien paga)
    private String clientName;
    private String clientRfc; // Opcional
    private String clientEmail;
    private String clientAddress;

    // Plan
    private String planName;
    private String planDescription;
    private String planDuration; // "Mensual"
    private BigDecimal amount;
    
    private LocalDateTime startDate;
    private LocalDateTime endDate;
}
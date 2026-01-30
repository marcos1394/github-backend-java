package com.quhealthy.payment_service.controller;

import com.quhealthy.payment_service.dto.pdf.PdfAppointmentReceiptDto;
import com.quhealthy.payment_service.dto.pdf.PdfInvoiceDto;
import com.quhealthy.payment_service.model.Subscription;
import com.quhealthy.payment_service.repository.SubscriptionRepository;
import com.quhealthy.payment_service.service.PdfGeneratorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final PdfGeneratorService pdfService;
    private final SubscriptionRepository subscriptionRepository;

    /**
     * 📄 Descargar Comprobante de Plan (Doctores)
     */
    @GetMapping("/invoice/{subscriptionId}")
    public ResponseEntity<byte[]> downloadInvoice(
            @PathVariable String subscriptionId,
            @AuthenticationPrincipal Long providerId) {

        // 1. Buscar Datos en BD
        Subscription sub = subscriptionRepository.findByExternalSubscriptionId(subscriptionId)
                .orElseThrow(() -> new RuntimeException("Suscripción no encontrada"));

        // 2. Mapear a DTO (Aquí podrías hacer llamadas a User Service para nombres reales)
        PdfInvoiceDto dto = PdfInvoiceDto.builder()
                .transactionId(sub.getExternalSubscriptionId())
                .paymentDate(sub.getUpdatedAt()) // Usar fecha real de pago
                .clientName("Doctor (ID " + providerId + ")") // TODO: Obtener nombre real
                .clientEmail("doctor@email.com")
                .planName("Plan QuHealthy") // TODO: Obtener del PlanRepository
                .planDescription("Suscripción mensual")
                .planDuration("Mensual")
                .amount(new BigDecimal("499.00")) // TODO: Obtener de BD
                .startDate(sub.getCurrentPeriodStart())
                .endDate(sub.getCurrentPeriodEnd())
                .build();

        // 3. Generar PDF
        byte[] pdfBytes = pdfService.generateInvoicePdf(dto);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=comprobante_" + subscriptionId + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }

    /**
     * 🩺 Descargar Recibo de Cita (Pacientes)
     * Este endpoint recibe los datos por POST (más fácil si el front ya tiene la info)
     * O podrías buscar en BD de citas si PaymentService tiene acceso.
     */
    @PostMapping("/appointment-receipt")
    public ResponseEntity<byte[]> downloadAppointmentReceipt(@RequestBody PdfAppointmentReceiptDto request) {
        
        byte[] pdfBytes = pdfService.generateAppointmentReceiptPdf(request);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=recibo_cita_" + request.getAppointmentId() + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }
}
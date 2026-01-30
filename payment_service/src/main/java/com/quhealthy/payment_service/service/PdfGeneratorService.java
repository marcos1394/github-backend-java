package com.quhealthy.payment_service.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import com.quhealthy.payment_service.dto.pdf.PdfAppointmentReceiptDto;
import com.quhealthy.payment_service.dto.pdf.PdfInvoiceDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Slf4j
@Service
public class PdfGeneratorService {

    // 🎨 PALETA DE COLORES (Tu marca)
    private static final Color COL_PRIMARY = new Color(139, 92, 246); // #8B5CF6
    private static final Color COL_DARK_BG = new Color(31, 41, 55);   // #1F2937
    private static final Color COL_TEXT_BODY = new Color(17, 24, 39); // #111827
    private static final Color COL_TEXT_LIGHT = new Color(107, 114, 128); // #6B7280

    // 🔤 FUENTES
    private static final Font FONT_TITLE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, Color.WHITE);
    private static final Font FONT_HEADER_BOLD = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, COL_PRIMARY);
    private static final Font FONT_BODY = FontFactory.getFont(FontFactory.HELVETICA, 10, COL_TEXT_BODY);
    private static final Font FONT_BODY_BOLD = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, COL_TEXT_BODY);
    private static final Font FONT_SMALL = FontFactory.getFont(FontFactory.HELVETICA, 8, COL_TEXT_LIGHT);

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd 'de' MMMM 'de' yyyy", new Locale("es", "MX"));
    private static final NumberFormat CURRENCY_FMT = NumberFormat.getCurrencyInstance(new Locale("es", "MX"));

    /**
     * 📄 Genera Comprobante de Pago de Plan (SaaS)
     */
    public byte[] generateInvoicePdf(PdfInvoiceDto data) {
        // Se agrega IOException al catch para cumplir con el contrato de try-with-resources
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 50, 50, 50, 50);
            PdfWriter writer = PdfWriter.getInstance(document, out);
            
            // Evento para Header y Footer
            writer.setPageEvent(new BrandingHeaderFooter());

            document.open();

            // 1. Título y Fecha
            PdfPTable titleTable = new PdfPTable(2);
            titleTable.setWidthPercentage(100);
            titleTable.addCell(getCell("COMPROBANTE DE PAGO", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, COL_TEXT_BODY), false));
            titleTable.addCell(getCell("Emisión: " + data.getPaymentDate().format(DATE_FMT), FONT_SMALL, true));
            document.add(titleTable);
            document.add(new Paragraph("\n"));

            // 2. Caja Principal (Plan y Monto)
            PdfPTable mainBox = new PdfPTable(2);
            mainBox.setWidthPercentage(100);
            mainBox.setSpacingBefore(10);
            
            // Lado Izquierdo: Plan
            PdfPCell leftCell = new PdfPCell();
            leftCell.setBorder(Rectangle.BOX);
            leftCell.setPadding(15);
            leftCell.addElement(new Paragraph("PLAN CONTRATADO", FONT_SMALL));
            leftCell.addElement(new Paragraph(data.getPlanName() + " (" + data.getPlanDuration() + ")", FONT_HEADER_BOLD));
            leftCell.addElement(new Paragraph(data.getPlanDescription(), FONT_SMALL));
            mainBox.addCell(leftCell);

            // Lado Derecho: Precio
            PdfPCell rightCell = new PdfPCell();
            rightCell.setBorder(Rectangle.BOX);
            rightCell.setPadding(15);
            rightCell.addElement(new Paragraph("MONTO PAGADO", FONT_SMALL));
            rightCell.addElement(new Paragraph(CURRENCY_FMT.format(data.getAmount()), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, COL_PRIMARY)));
            rightCell.addElement(new Paragraph("\nID Transacción:", FONT_SMALL));
            rightCell.addElement(new Paragraph(data.getTransactionId(), FONT_SMALL));
            mainBox.addCell(rightCell);

            document.add(mainBox);
            document.add(new Paragraph("\n"));

            // 3. Detalles del Cliente
            document.add(new Paragraph("FACTURADO A:", FONT_HEADER_BOLD));
            document.add(new Paragraph(data.getClientName(), FONT_BODY));
            document.add(new Paragraph(data.getClientEmail(), FONT_BODY));
            if(data.getClientAddress() != null) document.add(new Paragraph(data.getClientAddress(), FONT_SMALL));
            
            document.add(new Paragraph("\n"));

            // 4. Vigencia
            PdfPTable datesTable = new PdfPTable(2);
            datesTable.setWidthPercentage(100);
            datesTable.addCell(getCell("Inicio de Vigencia:", FONT_BODY_BOLD, false));
            datesTable.addCell(getCell(data.getStartDate().format(DATE_FMT), FONT_BODY, false));
            datesTable.addCell(getCell("Fin de Vigencia:", FONT_BODY_BOLD, false));
            datesTable.addCell(getCell(data.getEndDate().format(DATE_FMT), FONT_BODY, false));
            document.add(datesTable);

            // 5. Nota Legal
            document.add(new Paragraph("\n\n"));
            PdfPCell noteCell = new PdfPCell(new Paragraph("NOTA IMPORTANTE: Este documento es un comprobante de transacción interno. No es una factura fiscal (CFDI).", FONT_SMALL));
            noteCell.setBorder(Rectangle.TOP);
            noteCell.setPaddingTop(10);
            noteCell.setBorderColor(Color.LIGHT_GRAY);
            
            PdfPTable footerTable = new PdfPTable(1);
            footerTable.setWidthPercentage(100);
            footerTable.addCell(noteCell);
            document.add(footerTable);

            document.close();
            return out.toByteArray();

        } catch (DocumentException | IOException e) { // <--- CORRECCIÓN AQUÍ: Capturamos IOException
            log.error("Error generando PDF Invoice", e);
            throw new RuntimeException("Error generando PDF Invoice", e);
        }
    }

    /**
     * 🩺 Genera Recibo de Cita Médica (Para Paciente)
     */
    public byte[] generateAppointmentReceiptPdf(PdfAppointmentReceiptDto data) {
        // Se agrega IOException al catch para cumplir con el contrato de try-with-resources
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 50, 50, 50, 50);
            PdfWriter writer = PdfWriter.getInstance(document, out);
            writer.setPageEvent(new BrandingHeaderFooter());
            document.open();

            // Título Centrado
            Paragraph title = new Paragraph("¡Tu Cita está Confirmada!", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, COL_TEXT_BODY));
            title.setAlignment(Element.ALIGN_CENTER);
            document.add(title);
            
            Paragraph subTitle = new Paragraph("ID Cita: " + data.getAppointmentId(), FONT_SMALL);
            subTitle.setAlignment(Element.ALIGN_CENTER);
            document.add(subTitle);
            
            document.add(new Paragraph("\n\n"));

            // Tarjeta de Detalles
            PdfPTable table = new PdfPTable(2);
            table.setWidthPercentage(100);
            table.setSpacingBefore(20);

            // Servicio
            table.addCell(getCell("Servicio Agendado", FONT_HEADER_BOLD, false));
            table.addCell(getCell("Profesional", FONT_HEADER_BOLD, true));
            
            table.addCell(getCell(data.getServiceName(), FONT_BODY, false));
            table.addCell(getCell(data.getProviderName(), FONT_BODY, true));

            document.add(table);
            document.add(new Paragraph("\n"));

            // Fecha Destacada (Caja Gris)
            PdfPTable dateBox = new PdfPTable(1);
            dateBox.setWidthPercentage(100);
            PdfPCell dateCell = new PdfPCell();
            dateCell.setBackgroundColor(new Color(249, 250, 251)); // Gris muy claro
            dateCell.setPadding(20);
            dateCell.setBorder(Rectangle.NO_BORDER);
            
            Paragraph dateP = new Paragraph(data.getAppointmentDate().format(DATE_FMT), FONT_BODY_BOLD);
            dateP.setAlignment(Element.ALIGN_CENTER);
            Paragraph timeP = new Paragraph(data.getAppointmentDate().format(DateTimeFormatter.ofPattern("hh:mm a")), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 24, COL_PRIMARY));
            timeP.setAlignment(Element.ALIGN_CENTER);
            
            dateCell.addElement(dateP);
            dateCell.addElement(timeP);
            dateBox.addCell(dateCell);
            document.add(dateBox);

            document.add(new Paragraph("\n\n"));

            // Resumen de Pago
            document.add(new Paragraph("Resumen del Pago", FONT_HEADER_BOLD));
            document.add(new Paragraph("Total Pagado: " + CURRENCY_FMT.format(data.getAmountPaid()), FONT_BODY));
            document.add(new Paragraph("Método: " + data.getPaymentMethod(), FONT_SMALL));

            document.close();
            return out.toByteArray();
        } catch (DocumentException | IOException e) { // <--- CORRECCIÓN AQUÍ: Capturamos IOException
            log.error("Error generando PDF Cita", e);
            throw new RuntimeException("Error generando PDF Cita", e);
        }
    }

    // --- UTILS ---

    private PdfPCell getCell(String text, Font font, boolean alignRight) {
        PdfPCell cell = new PdfPCell(new Paragraph(text, font));
        cell.setBorder(Rectangle.NO_BORDER);
        if (alignRight) cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        return cell;
    }

    // --- CLASE INTERNA PARA HEADER/FOOTER ---
    private class BrandingHeaderFooter extends PdfPageEventHelper {
        @Override
        public void onStartPage(PdfWriter writer, Document document) {
            // Fondo Oscuro Header
            PdfContentByte cb = writer.getDirectContentUnder();
            cb.saveState();
            cb.setColorFill(COL_DARK_BG);
            cb.rectangle(0, document.getPageSize().getHeight() - 100, document.getPageSize().getWidth(), 100);
            cb.fill();
            cb.restoreState();

            // Logo (o Texto si falla)
            try {
                // Intenta cargar src/main/resources/static/quhealthy.png
                Image logo = Image.getInstance(new ClassPathResource("static/quhealthy.png").getURL());
                logo.scaleToFit(120, 50);
                logo.setAbsolutePosition(50, document.getPageSize().getHeight() - 70);
                document.add(logo);
            } catch (Exception e) {
                // Fallback Texto
                ColumnText.showTextAligned(writer.getDirectContent(), Element.ALIGN_LEFT, 
                    new Phrase("QuHealthy", FONT_TITLE), 50, document.getPageSize().getHeight() - 60, 0);
            }
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            ColumnText.showTextAligned(writer.getDirectContent(), Element.ALIGN_CENTER, 
                new Phrase("www.quhealthy.org", FONT_SMALL), 
                (document.right() - document.left()) / 2 + document.leftMargin(), 
                document.bottom() - 10, 0);
        }
    }
}
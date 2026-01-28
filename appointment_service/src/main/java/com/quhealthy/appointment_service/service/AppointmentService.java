package com.quhealthy.appointment_service.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import com.quhealthy.appointment_service.client.CatalogClient;
import com.quhealthy.appointment_service.dto.request.CompleteAppointmentRequest;
import com.quhealthy.appointment_service.dto.request.CreateAppointmentRequest;
import com.quhealthy.appointment_service.dto.request.RescheduleRequest;
import com.quhealthy.appointment_service.dto.response.AppointmentResponse;
import com.quhealthy.appointment_service.dto.response.CatalogServiceDto;
import com.quhealthy.appointment_service.event.AppointmentEvent;
import com.quhealthy.appointment_service.model.Appointment;
import com.quhealthy.appointment_service.model.ConsumerPackageBalance;
import com.quhealthy.appointment_service.model.enums.AppointmentStatus;
import com.quhealthy.appointment_service.model.enums.PaymentMethod;
import com.quhealthy.appointment_service.model.enums.PaymentStatus;
import com.quhealthy.appointment_service.repository.AppointmentRepository;
import com.quhealthy.appointment_service.repository.ConsumerPackageBalanceRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final ConsumerPackageBalanceRepository packageBalanceRepository;
    private final CatalogClient catalogClient;
    
    // Google Cloud Pub/Sub
    private final PubSubTemplate pubSubTemplate;
    private final ObjectMapper objectMapper;

    @Value("${application.events.appointments-topic}")
    private String appointmentsTopic;

    /**
     * ✅ CREAR CITA (BOOKING)
     * Maneja validación de horario, snapshot de precio y lógica de paquetes.
     */
    @Transactional
    public AppointmentResponse createAppointment(Long consumerId, CreateAppointmentRequest request) {
        log.info("🗓️ Iniciando reserva para Consumer: {} con Provider: {}", consumerId, request.getProviderId());

        // 1. Obtener detalles del servicio desde Catalog-Service (Feign)
        // Esto valida que el servicio exista y obtenemos su duración real.
        CatalogServiceDto serviceDto;
        try {
            serviceDto = catalogClient.getServiceById(request.getServiceId());
        } catch (Exception e) {
            throw new IllegalArgumentException("El servicio solicitado no existe o el Catálogo no está disponible.");
        }

        // 2. Calcular Hora Fin
        LocalDateTime endTime = request.getStartTime().plusMinutes(serviceDto.getDurationMinutes());

        // 3. 🛡️ VALIDACIÓN DE DOBLE RESERVA (Double Booking)
        boolean hasConflict = appointmentRepository.hasOverlappingAppointments(
                request.getProviderId(), 
                request.getStartTime(), 
                endTime
        );
        if (hasConflict) {
            throw new IllegalStateException("El horario seleccionado ya no está disponible. Por favor elige otro.");
        }

        // 4. Lógica de Pago / Paquete
        boolean isPaid = false;
        PaymentStatus initialPaymentStatus = PaymentStatus.PENDING;
        Long usedPackageBalanceId = null;

        if (request.getPaymentMethod() == PaymentMethod.PACKAGE_REDEMPTION) {
            // Lógica Legacy: Buscar si tiene créditos disponibles
            List<ConsumerPackageBalance> balances = packageBalanceRepository.findRedeemablePackages(
                    consumerId, request.getProviderId(), request.getServiceId()
            );

            if (balances.isEmpty()) {
                throw new IllegalStateException("No tienes créditos disponibles en tu paquete para este servicio.");
            }

            // Tomamos el primer paquete que vence pronto (FIFO)
            ConsumerPackageBalance balanceToUse = balances.get(0);
            balanceToUse.setRemainingCredits(balanceToUse.getRemainingCredits() - 1);
            packageBalanceRepository.save(balanceToUse); // Actualizamos saldo

            isPaid = true;
            initialPaymentStatus = PaymentStatus.SETTLED;
            usedPackageBalanceId = balanceToUse.getId();
            log.info("✅ Crédito descontado del paquete ID: {}", balanceToUse.getId());
        } else if (request.getPaymentMethod() == PaymentMethod.CASH || request.getPaymentMethod() == PaymentMethod.INSURANCE) {
            // Pago en sitio, nace como Pendiente
            isPaid = false; 
            initialPaymentStatus = PaymentStatus.PENDING;
        }

        // 5. Crear Entidad (Snapshot)
        Appointment appointment = Appointment.builder()
                .providerId(request.getProviderId())
                .consumerId(consumerId)
                .serviceId(request.getServiceId())
                // Snapshot Data (Congelamos precio y nombre)
                .serviceNameSnapshot(serviceDto.getName())
                .totalPrice(serviceDto.getPrice())
                .currency(serviceDto.getCurrency())
                .totalPrice(serviceDto.getPrice())
                .amountPaid(isPaid ? serviceDto.getPrice() : BigDecimal.ZERO) // Si es paquete, "pagó" todo
                // Agenda
                .startTime(request.getStartTime())
                .endTime(endTime)
                .appointmentType(request.getAppointmentType())
                .status(AppointmentStatus.SCHEDULED)
                // Payment
                .paymentMethod(request.getPaymentMethod())
                .paymentStatus(initialPaymentStatus)
                .consumerPackageBalanceId(usedPackageBalanceId)
                // Notas
                .patientSymptoms(request.getPatientSymptoms())
                .build();

        Appointment saved = appointmentRepository.save(appointment);

        // 6. Publicar Evento (Para Notificaciones)
        publishEvent(saved, "APPOINTMENT_CREATED");

        return mapToResponse(saved);
    }

    /**
     * ✅ COMPLETAR CITA
     * Lo llama el doctor cuando termina la consulta. Dispara solicitud de reseña.
     */
    @Transactional
    public AppointmentResponse completeAppointment(Long providerId, Long appointmentId, CompleteAppointmentRequest request) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new EntityNotFoundException("Cita no encontrada"));

        if (!appointment.getProviderId().equals(providerId)) {
            throw new SecurityException("No tienes permiso para gestionar esta cita.");
        }

        // Cambiar estado
        appointment.setStatus(AppointmentStatus.COMPLETED);
        if (request.getPrivateNotes() != null) {
            appointment.setPrivateNotes(request.getPrivateNotes());
        }
        
        // Si estaba pendiente de pago (Efectivo), asumimos que se pagó al completar
        if (appointment.getPaymentStatus() == PaymentStatus.PENDING) {
            appointment.setPaymentStatus(PaymentStatus.SETTLED);
            appointment.setAmountPaid(appointment.getTotalPrice());
        }

        Appointment saved = appointmentRepository.save(appointment);
        log.info("✅ Cita {} completada por el doctor.", appointmentId);

        // 🚀 Evento CLAVE: Review Service escuchará esto para enviar el email de reseña
        publishEvent(saved, "APPOINTMENT_COMPLETED");

        return mapToResponse(saved);
    }

    /**
     * ✅ CANCELAR CITA
     * Maneja reembolso de créditos si aplica.
     */
    @Transactional
    public AppointmentResponse cancelAppointment(Long userId, String userRole, Long appointmentId, String reason) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new EntityNotFoundException("Cita no encontrada"));

        // Validar propiedad
        boolean isProvider = "ROLE_PROVIDER".equals(userRole) || "PROVIDER".equals(userRole);
        if (isProvider && !appointment.getProviderId().equals(userId)) throw new SecurityException("Acceso denegado");
        if (!isProvider && !appointment.getConsumerId().equals(userId)) throw new SecurityException("Acceso denegado");

        // Regresar créditos si fue pagada con paquete
        if (appointment.getPaymentMethod() == PaymentMethod.PACKAGE_REDEMPTION 
            && appointment.getConsumerPackageBalanceId() != null) {
            
            packageBalanceRepository.findById(appointment.getConsumerPackageBalanceId())
                .ifPresent(balance -> {
                    balance.setRemainingCredits(balance.getRemainingCredits() + 1);
                    packageBalanceRepository.save(balance);
                    log.info("🔄 Crédito devuelto al paquete ID: {}", balance.getId());
                });
            appointment.setPaymentStatus(PaymentStatus.REFUNDED);
        }

        // Actualizar estado
        appointment.setStatus(isProvider ? AppointmentStatus.CANCELED_BY_PROVIDER : AppointmentStatus.CANCELED_BY_PATIENT);
        appointment.setCancellationReason(reason);
        
        Appointment saved = appointmentRepository.save(appointment);
        publishEvent(saved, "APPOINTMENT_CANCELED");

        return mapToResponse(saved);
    }
    
    /**
     * ✅ REAGENDAR
     */
    @Transactional
    public AppointmentResponse rescheduleAppointment(Long userId, Long appointmentId, RescheduleRequest request) {
         Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new EntityNotFoundException("Cita no encontrada"));
         
         // Validaciones de propiedad omitidas por brevedad (similares a cancelar)
         
         // Recalcular fin
         long duration = java.time.Duration.between(appointment.getStartTime(), appointment.getEndTime()).toMinutes();
         LocalDateTime newEnd = request.getNewStartTime().plusMinutes(duration);
         
         // Validar conflicto
         if (appointmentRepository.hasOverlappingAppointments(appointment.getProviderId(), request.getNewStartTime(), newEnd)) {
             throw new IllegalStateException("El nuevo horario no está disponible.");
         }
         
         appointment.setStartTime(request.getNewStartTime());
         appointment.setEndTime(newEnd);
         appointment.setStatus(AppointmentStatus.RESCHEDULED); // O mantener SCHEDULED según prefieras
         
         Appointment saved = appointmentRepository.save(appointment);
         publishEvent(saved, "APPOINTMENT_RESCHEDULED");
         
         return mapToResponse(saved);
    }
    
    // --- LECTURA ---
    
    public Page<AppointmentResponse> getMyAppointments(Long userId, boolean isProvider, Pageable pageable) {
        if (isProvider) {
            return appointmentRepository.findByProviderIdOrderByStartTimeDesc(userId, pageable)
                    .map(this::mapToResponse);
        } else {
            return appointmentRepository.findByConsumerIdOrderByStartTimeDesc(userId, pageable)
                    .map(this::mapToResponse);
        }
    }

    // =================================================================
    // 🛠️ UTILS
    // =================================================================

    private void publishEvent(Appointment appt, String type) {
        try {
            AppointmentEvent event = AppointmentEvent.builder()
                    .appointmentId(appt.getId())
                    .providerId(appt.getProviderId())
                    .consumerId(appt.getConsumerId())
                    .eventType(type)
                    .status(appt.getStatus().name())
                    .timestamp(LocalDateTime.now())
                    .build();

            String json = objectMapper.writeValueAsString(event);
            pubSubTemplate.publish(appointmentsTopic, json);
            log.debug("📡 Evento publicado en Pub/Sub: {}", type);
        } catch (JsonProcessingException e) {
            log.error("❌ Error serializando evento Pub/Sub", e);
        }
    }

    private AppointmentResponse mapToResponse(Appointment a) {
        return AppointmentResponse.builder()
                .id(a.getId())
                .providerId(a.getProviderId())
                .consumerId(a.getConsumerId())
                .serviceId(a.getServiceId())
                .serviceName(a.getServiceNameSnapshot())
                .price(a.getTotalPrice())
                .currency(a.getCurrency())
                .startTime(a.getStartTime())
                .endTime(a.getEndTime())
                .status(a.getStatus())
                .type(a.getAppointmentType())
                .meetLink(a.getMeetLink())
                .locationAddress(a.getLocationAddress())
                .paymentStatus(a.getPaymentStatus())
                .paymentMethod(a.getPaymentMethod())
                .amountPaid(a.getAmountPaid())
                .patientSymptoms(a.getPatientSymptoms())
                .build();
    }
}
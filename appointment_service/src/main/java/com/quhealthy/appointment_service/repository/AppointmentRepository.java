package com.quhealthy.appointment_service.repository;

import com.quhealthy.appointment_service.model.Appointment;
import com.quhealthy.appointment_service.model.enums.AppointmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    /**
     * 📅 VALIDACIÓN DE TRASLAPE (Double Booking Check)
     * Verifica si existe alguna cita que NO esté cancelada y cuyo horario choque con el solicitado.
     */
    @Query("""
        SELECT COUNT(a) > 0 FROM Appointment a 
        WHERE a.providerId = :providerId 
        AND a.status NOT IN ('CANCELED_BY_PATIENT', 'CANCELED_BY_PROVIDER', 'NO_SHOW')
        AND (a.startTime < :endTime AND a.endTime > :startTime)
    """)
    boolean hasOverlappingAppointments(
            @Param("providerId") Long providerId, 
            @Param("startTime") LocalDateTime startTime, 
            @Param("endTime") LocalDateTime endTime
    );

    /**
     * 📅 VISTA DE CALENDARIO (Provider)
     * Obtiene citas en un rango de fechas (ej: Semana o Mes actual).
     */
    List<Appointment> findByProviderIdAndStartTimeBetweenOrderByStartTimeAsc(
            Long providerId, 
            LocalDateTime start, 
            LocalDateTime end
    );

    /**
     * 📋 HISTORIAL DE PACIENTE
     */
    Page<Appointment> findByConsumerIdOrderByStartTimeDesc(Long consumerId, Pageable pageable);

    /**
     * 📋 HISTORIAL DE PROVEEDOR (Gestión por Estado)
     */
    Page<Appointment> findByProviderIdAndStatus(Long providerId, AppointmentStatus status, Pageable pageable);
    
    /**
     * 📋 HISTORIAL DE PROVEEDOR (Todas)
     */
    Page<Appointment> findByProviderIdOrderByStartTimeDesc(Long providerId, Pageable pageable);

    /**
     * ✅ EL MÉTODO QUE FALTABA (Vital para CalendarService)
     * Busca citas confirmadas que ocupen espacio en el calendario.
     * Excluye las canceladas.
     */
    @Query("""
        SELECT a FROM Appointment a 
        WHERE a.providerId = :providerId 
        AND a.status NOT IN ('CANCELED_BY_PATIENT', 'CANCELED_BY_PROVIDER', 'NO_SHOW')
        AND (a.startTime < :end AND a.endTime > :start)
    """)
    List<Appointment> findConfirmedBetween(
            @Param("providerId") Long providerId, 
            @Param("start") LocalDateTime start, 
            @Param("end") LocalDateTime end
    );
}
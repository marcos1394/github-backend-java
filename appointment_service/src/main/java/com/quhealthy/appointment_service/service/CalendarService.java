package com.quhealthy.appointment_service.service;

import com.quhealthy.appointment_service.model.Appointment;
import com.quhealthy.appointment_service.model.ProviderSchedule;
import com.quhealthy.appointment_service.model.TimeBlock;
import com.quhealthy.appointment_service.repository.AppointmentRepository;
import com.quhealthy.appointment_service.repository.ProviderScheduleRepository;
import com.quhealthy.appointment_service.repository.TimeBlockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CalendarService {

    private final ProviderScheduleRepository scheduleRepository;
    private final TimeBlockRepository timeBlockRepository;
    private final AppointmentRepository appointmentRepository; // Ya existente en este microservicio

    /**
     * Actualizar horarios (Estrategia: Wipe & Replace)
     */
    @Transactional
    public List<ProviderSchedule> updateOperatingHours(Long providerId, List<ProviderSchedule> newSchedules) {
        log.info("🗓️ Actualizando horarios para Provider ID: {}", providerId);
        
        // 1. Borrar anteriores
        scheduleRepository.deleteByProviderId(providerId);
        
        // 2. Asignar ID y guardar nuevos
        newSchedules.forEach(s -> s.setProviderId(providerId));
        return scheduleRepository.saveAll(newSchedules);
    }

    /**
     * 🧠 LÓGICA PRINCIPAL: Calcular Slots Disponibles
     * Migración optimizada de tu getProviderAvailability
     */
    @Transactional(readOnly = true)
    public List<LocalDateTime> getAvailableSlots(Long providerId, LocalDate startDate, LocalDate endDate, int durationMinutes) {
        log.info("🔍 Buscando slots para Provider {} entre {} y {} (Duración: {}m)", providerId, startDate, endDate, durationMinutes);

        // 1. Cargar configuración en memoria (Horarios Base)
        // Map<DayOfWeek, Schedule> para acceso rápido
        Map<DayOfWeek, ProviderSchedule> scheduleMap = scheduleRepository.findByProviderId(providerId).stream()
                .collect(Collectors.toMap(ProviderSchedule::getDayOfWeek, s -> s));

        // 2. Cargar Ocupación (Citas y Bloqueos) en el rango
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);

        List<Appointment> appointments = appointmentRepository.findConfirmedBetween(providerId, startDateTime, endDateTime);
        List<TimeBlock> blocks = timeBlockRepository.findOverlappingBlocks(providerId, startDateTime, endDateTime);

        List<LocalDateTime> availableSlots = new ArrayList<>();

        // 3. Iterar día por día
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            ProviderSchedule schedule = scheduleMap.get(date.getDayOfWeek());
            
            // Si no trabaja ese día, saltar
            if (schedule == null) continue;

            // Generar slots para este día
            LocalDateTime slotStart = date.atTime(schedule.getStartTime());
            LocalDateTime dayEnd = date.atTime(schedule.getEndTime());

            // Loop intra-día
            while (slotStart.plusMinutes(durationMinutes).isBefore(dayEnd) || slotStart.plusMinutes(durationMinutes).isEqual(dayEnd)) {
                
                LocalDateTime slotEnd = slotStart.plusMinutes(durationMinutes);
                
                // A. Validar descanso (Break)
                if (isDuringBreak(slotStart, slotEnd, schedule)) {
                    slotStart = slotStart.plusMinutes(durationMinutes); // Avanzar
                    continue;
                }

                // B. Validar conflictos (Citas y Bloqueos)
                if (!isOverlapping(slotStart, slotEnd, appointments, blocks)) {
                    availableSlots.add(slotStart);
                }

                // Avanzar al siguiente slot
                // NOTA: Aquí puedes decidir si avanzas por 'duration' o por un 'step' fijo (ej: cada 15 min)
                slotStart = slotStart.plusMinutes(durationMinutes); 
            }
        }
        
        return availableSlots;
    }

    // --- Helpers Privados ---

    private boolean isDuringBreak(LocalDateTime start, LocalDateTime end, ProviderSchedule schedule) {
        if (schedule.getBreakStart() == null || schedule.getBreakEnd() == null) return false;
        
        LocalDateTime breakStart = start.toLocalDate().atTime(schedule.getBreakStart());
        LocalDateTime breakEnd = start.toLocalDate().atTime(schedule.getBreakEnd());

        // Si el slot toca el horario de descanso
        return start.isBefore(breakEnd) && end.isAfter(breakStart);
    }

    private boolean isOverlapping(LocalDateTime start, LocalDateTime end, List<Appointment> appts, List<TimeBlock> blocks) {
        // Checar citas
        boolean apptConflict = appts.stream().anyMatch(a -> 
            start.isBefore(a.getEndTime()) && end.isAfter(a.getStartTime())
        );
        if (apptConflict) return true;

        // Checar bloqueos manuales
        return blocks.stream().anyMatch(b -> 
            start.isBefore(b.getEndDateTime()) && end.isAfter(b.getStartDateTime())
        );
    }
}
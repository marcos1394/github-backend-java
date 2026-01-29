package com.quhealthy.appointment_service.repository;

import com.quhealthy.appointment_service.model.ProviderSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProviderScheduleRepository extends JpaRepository<ProviderSchedule, Long> {
    
    // Obtener la configuración semanal del doctor
    List<ProviderSchedule> findByProviderId(Long providerId);
    
    // Borrar configuración previa (para actualizaciones limpias)
    void deleteByProviderId(Long providerId);
}
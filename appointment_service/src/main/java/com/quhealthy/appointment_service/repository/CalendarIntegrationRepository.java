package com.quhealthy.appointment_service.repository;

import com.quhealthy.appointment_service.model.CalendarIntegration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CalendarIntegrationRepository extends JpaRepository<CalendarIntegration, Long> {
    
    // Buscar la integración por el ID del Doctor (Provider)
    Optional<CalendarIntegration> findByProviderId(Long providerId);
}
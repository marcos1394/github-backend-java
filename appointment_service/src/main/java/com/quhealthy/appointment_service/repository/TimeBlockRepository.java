package com.quhealthy.appointment_service.repository;

import com.quhealthy.appointment_service.model.TimeBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TimeBlockRepository extends JpaRepository<TimeBlock, Long> {

    // Método existente (probablemente ya lo tenías) para buscar bloqueos en un rango
    @Query("SELECT t FROM TimeBlock t WHERE t.providerId = :providerId AND " +
           "((t.startDateTime < :end AND t.endDateTime > :start))")
    List<TimeBlock> findOverlappingBlocks(@Param("providerId") Long providerId, 
                                          @Param("start") LocalDateTime start, 
                                          @Param("end") LocalDateTime end);

    // ✅ MÉTODO NUEVO QUE FALTABA (Soluciona el error 1)
    Optional<TimeBlock> findByExternalId(String externalId);
}
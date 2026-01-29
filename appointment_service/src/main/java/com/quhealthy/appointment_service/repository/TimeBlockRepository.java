package com.quhealthy.appointment_service.repository;

import com.quhealthy.appointment_service.model.TimeBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TimeBlockRepository extends JpaRepository<TimeBlock, Long> {

    /**
     * ✅ BUSCAR BLOQUEOS QUE CHOCAN CON UN RANGO
     * Retorna cualquier bloqueo (vacaciones, comida) que interfiera con las fechas dadas.
     */
    @Query("SELECT b FROM TimeBlock b WHERE b.providerId = :providerId " +
           "AND b.startDateTime < :end AND b.endDateTime > :start")
    List<TimeBlock> findOverlappingBlocks(
            @Param("providerId") Long providerId, 
            @Param("start") LocalDateTime start, 
            @Param("end") LocalDateTime end
    );
}
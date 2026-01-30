package com.quhealthy.appointment_service.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "time_blocks")
public class TimeBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "provider_id", nullable = false)
    private Long providerId;

    @Column(nullable = false)
    private LocalDateTime startDateTime;

    @Column(nullable = false)
    private LocalDateTime endDateTime;

    private String reason; // Ej: "Almuerzo", "Google Calendar: Cena"

    // ✅ CAMPO NUEVO 1: ID externo para sincronización (Google/Outlook)
    @Column(name = "external_id", unique = true)
    private String externalId;

    // ✅ CAMPO NUEVO 2: Para distinguir si fue creado manualmente o por sync
    // Usamos @Builder.Default para que por defecto sea true (manual)
    @Builder.Default
    @Column(name = "is_manual")
    private boolean isManual = true; 
}
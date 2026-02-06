package com.quhealthy.onboarding_service.model;

import com.quhealthy.onboarding_service.model.enums.ValidationSource;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "provider_licenses")
public class ProfessionalLicense extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "provider_id", nullable = false)
    private Long providerId;

    @Column(name = "license_number", nullable = false, length = 20)
    private String licenseNumber; // El número de cédula extraído

    @Column(name = "institution_name", nullable = false)
    private String institutionName; // Ej: "Universidad Nacional Autónoma de México"

    @Column(name = "career_name", nullable = false)
    private String careerName; // Ej: "Médico Cirujano"

    @Column(name = "year_issued")
    private Integer yearIssued; // Año de expedición

    // --- AUDITORÍA DE VALIDACIÓN ---

    @Column(name = "is_verified", nullable = false)
    private boolean isVerified;

    @Enumerated(EnumType.STRING)
    @Column(name = "validation_source")
    private ValidationSource validationSource; // API_SEP, MANUAL_ADMIN, AI_PREDICTION

    @Column(name = "document_url", nullable = false)
    private String documentUrl; // URL segura en Google Storage (PDF/JPG)
}
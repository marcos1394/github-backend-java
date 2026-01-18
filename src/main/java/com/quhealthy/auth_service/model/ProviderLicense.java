package com.quhealthy.auth_service.model;

import com.quhealthy.auth_service.model.enums.Gender;
import com.quhealthy.auth_service.model.enums.LicenseStatus;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode; // <--- 1. IMPORTAR ESTO
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes; // <--- 2. IMPORTAR ESTO

import java.time.LocalDateTime;
import java.util.Map; // <--- 3. IMPORTAR ESTO

@Data
@Entity
@Table(name = "provider_licenses")
public class ProviderLicense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Relación 1 a 1 con el Proveedor
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id", nullable = false, unique = true)
    private Provider provider;

    // --- Datos de la Cédula ---
    @Column(name = "license_number", unique = true)
    private String licenseNumber;

    @Column(name = "full_name")
    private String fullName;

    @Enumerated(EnumType.STRING)
    private Gender gender;

    private String profession;

    @Column(name = "issue_year")
    private Integer issueYear;

    private String institution;

    @Column(name = "license_type")
    private String licenseType; // "C1", "A1", etc.

    // --- Estado ---
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LicenseStatus status = LicenseStatus.PENDING;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    // --- IA y Documentos ---
    @Column(name = "document_url", columnDefinition = "TEXT")
    private String documentUrl;

    // Datos extraídos para comparar con los reales
    @Column(name = "license_number_extracted")
    private String licenseNumberExtracted;

    @Column(name = "profession_extracted")
    private String professionExtracted;

    // =================================================================
    // 🔧 LA CORRECCIÓN: JSONB Mapping
    // =================================================================
    // Cambiamos String por Map<String, Object> y agregamos la anotación.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extracted_data", columnDefinition = "jsonb")
    private Map<String, Object> extractedData;

    // --- Timestamps ---
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
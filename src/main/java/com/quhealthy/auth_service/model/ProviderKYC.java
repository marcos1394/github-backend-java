package com.quhealthy.auth_service.model;

import com.quhealthy.auth_service.model.enums.KYCStatus;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "provider_kyc")
public class ProviderKYC {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Relación 1 a 1 con Provider
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id", nullable = false, unique = true)
    private Provider provider;

    @Column(name = "kyc_session_id", unique = true)
    private String kycSessionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kyc_status", nullable = false)
    private KYCStatus kycStatus = KYCStatus.NOT_STARTED;

    // --- Documentos ---
    @Column(name = "document_type")
    private String documentType; // "INE", "PASSPORT"

    @Column(name = "document_front_url", columnDefinition = "TEXT")
    private String documentFrontUrl;

    @Column(name = "document_back_url", columnDefinition = "TEXT")
    private String documentBackUrl;

    // --- Datos Extraídos por IA ---
    // En Java guardamos el JSON crudo como String. 
    // Cuando lo necesites usar, usaremos Jackson (ObjectMapper) para convertirlo a Objeto.
    @Column(name = "extracted_data", columnDefinition = "jsonb")
    private String extractedData; 

    @Column(name = "curp")
    private String curp;

    @Column(name = "rfc")
    private String rfc;

    // --- Auditoría ---
    @Column(name = "verification_date")
    private LocalDateTime verificationDate;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
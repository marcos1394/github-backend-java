package com.quhealthy.onboarding_service.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "provider_fiscal_info")
public class FiscalInformation extends BaseEntity {

    @Id
    @Column(name = "provider_id")
    private Long providerId;

    @Column(name = "rfc", nullable = false, length = 13, unique = true)
    private String rfc;

    @Column(name = "legal_name", nullable = false)
    private String legalName; // Razón Social (debe coincidir con la constancia)

    @Column(name = "tax_regime_code")
    private String taxRegimeCode; // Ej: "626" (Régimen Simplificado)

    @Column(name = "postal_code", length = 10)
    private String postalCode;

    @Column(name = "constancia_fiscal_url")
    private String constanciaFiscalUrl; // URL del PDF en Storage

    @Column(name = "last_validated_at")
    private java.time.LocalDateTime lastValidatedAt;
}
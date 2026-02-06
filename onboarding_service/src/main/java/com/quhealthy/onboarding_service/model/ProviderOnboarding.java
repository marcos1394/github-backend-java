package com.quhealthy.onboarding_service.model;

import com.quhealthy.onboarding_service.model.enums.OnboardingStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.Map;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
@Table(name = "provider_onboarding_status")
public class ProviderOnboarding extends BaseEntity {

    @Id
    @Column(name = "provider_id")
    private Long providerId; // PK Manual (Asignada, no generada)

    // --- CONFIGURACIÓN DEL PLAN (CACHE) ---
    @Column(name = "selected_plan_id")
    private Long selectedPlanId;

    // --- ESTADOS DE CADA PASO ---

    @Enumerated(EnumType.STRING)
    @Column(name = "profile_status", nullable = false)
    @Builder.Default
    private OnboardingStatus profileStatus = OnboardingStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "kyc_status", nullable = false)
    @Builder.Default
    private OnboardingStatus kycStatus = OnboardingStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "license_status", nullable = false)
    @Builder.Default
    private OnboardingStatus licenseStatus = OnboardingStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "fiscal_status", nullable = false)
    @Builder.Default
    private OnboardingStatus fiscalStatus = OnboardingStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "marketplace_status", nullable = false)
    @Builder.Default
    private OnboardingStatus marketplaceStatus = OnboardingStatus.PENDING;

    // --- FEEDBACK Y CONTROL ---

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rejection_reasons", columnDefinition = "jsonb")
    private Map<String, String> rejectionReasons;

    @Column(name = "attempt_count")
    @Builder.Default
    private int attemptCount = 0;
}
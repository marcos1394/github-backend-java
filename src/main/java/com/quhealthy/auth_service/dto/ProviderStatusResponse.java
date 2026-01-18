package com.quhealthy.auth_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ProviderStatusResponse {

    private OnboardingStatus onboardingStatus;
    private PlanDetails planDetails;
    private ProviderDetails providerDetails;

    @Data
    @Builder
    public static class OnboardingStatus {
        private StatusDetail kyc;
        private LicenseDetail license;
        private ConfigDetail marketplace;
        private ConfigDetail blocks;
    }

    @Data
    @Builder
    public static class StatusDetail {
        private String status;      // approved, pending, not_started
        private boolean isComplete;
    }

    @Data
    @Builder
    public static class LicenseDetail {
        private boolean isRequired;
        private String status;
        private boolean isComplete;
    }

    @Data
    @Builder
    public static class ConfigDetail {
        @JsonProperty("isConfigured") // Para mantener consistencia con tu JSON anterior
        private boolean isConfigured;
    }

    @Data
    @Builder
    public static class PlanDetails {
        private Long planId;
        private String planName;
        private boolean hasActivePlan;
        private String planStatus; // active, trial, expired
        private LocalDateTime endDate;
        private Permissions permissions;
    }

    @Data
    @Builder
    public static class Permissions {
        private boolean quMarketAccess;
        private boolean quBlocksAccess;
        private int marketingLevel;
        private int supportLevel;
        private boolean advancedReports;
        private int userManagement;
        private boolean allowAdvancePayments;
        
        // Usamos String para poder enviar "Ilimitados" o el número
        private String maxAppointments;
        private String maxProducts;
        private String maxCourses;
    }

    @Data
    @Builder
    public static class ProviderDetails {
        private Long parentCategoryId;
        private String email;
        private String name;
        private String archetype;
        private String stripeAccountId;
    }
}
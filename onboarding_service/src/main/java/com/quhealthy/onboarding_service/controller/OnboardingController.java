package com.quhealthy.onboarding_service.controller;

import com.quhealthy.onboarding_service.dto.response.OnboardingStatusResponse;
import com.quhealthy.onboarding_service.model.ProviderOnboarding;
import com.quhealthy.onboarding_service.repository.ProviderOnboardingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/onboarding")
@RequiredArgsConstructor
public class OnboardingController {

    private final ProviderOnboardingRepository onboardingRepository;

    @GetMapping("/status")
    public ResponseEntity<OnboardingStatusResponse> getStatus(@RequestHeader("X-User-Id") Long userId) {

        ProviderOnboarding status = onboardingRepository.findById(userId)
                .orElse(ProviderOnboarding.builder()
                        .providerId(userId)
                        // Valores por defecto si es la primera vez que entra
                        .build());

        // Calculo de porcentaje (Simple: 3 pasos principales implementados)
        int steps = 0;
        if (status.getProfileStatus().isFinished()) steps++;
        if (status.getKycStatus().isFinished()) steps++;
        if (status.getLicenseStatus().isFinished()) steps++;

        int percentage = (steps * 100) / 3;

        return ResponseEntity.ok(OnboardingStatusResponse.builder()
                .providerId(userId)
                .profileStatus(status.getProfileStatus().name())
                .kycStatus(status.getKycStatus().name())
                .licenseStatus(status.getLicenseStatus().name())
                .fiscalStatus(status.getFiscalStatus().name())
                .rejectionReasons(status.getRejectionReasons())
                .completionPercentage(percentage)
                .build());
    }
}
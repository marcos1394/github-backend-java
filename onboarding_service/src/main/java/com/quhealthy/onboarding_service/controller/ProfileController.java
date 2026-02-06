package com.quhealthy.onboarding_service.controller;

import com.quhealthy.onboarding_service.dto.request.UpdateProfileRequest;
import com.quhealthy.onboarding_service.dto.response.ProfileResponse;
import com.quhealthy.onboarding_service.repository.ProviderProfileRepository;
import com.quhealthy.onboarding_service.service.ProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/onboarding/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;
    private final ProviderProfileRepository profileRepository;

    /**
     * Actualiza o Crea el perfil (Paso 1).
     */
    @PutMapping
    public ResponseEntity<Void> updateProfile(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody UpdateProfileRequest request) {

        profileService.updateProfile(userId, request);
        return ResponseEntity.ok().build();
    }

    /**
     * Obtiene la información del perfil actual.
     */
    @GetMapping
    public ResponseEntity<ProfileResponse> getProfile(@RequestHeader("X-User-Id") Long userId) {
        return profileRepository.findById(userId)
                .map(p -> ResponseEntity.ok(ProfileResponse.builder()
                        .providerId(p.getProviderId())
                        .businessName(p.getBusinessName())
                        .bio(p.getBio())
                        .profileImageUrl(p.getProfileImageUrl())
                        .address(p.getAddress())
                        .latitude(p.getLatitude())
                        .longitude(p.getLongitude())
                        .googlePlaceId(p.getGooglePlaceId())
                        .websiteUrl(p.getWebsiteUrl())
                        .contactPhone(p.getContactPhone())
                        .categoryId(p.getCategoryId())
                        .subCategoryId(p.getSubCategoryId())
                        .slug(p.getSlug())
                        .build()))
                .orElse(ResponseEntity.notFound().build());
    }
}
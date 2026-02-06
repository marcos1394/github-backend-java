package com.quhealthy.onboarding_service.service;

import com.github.slugify.Slugify;
import com.quhealthy.onboarding_service.dto.request.UpdateProfileRequest;
import com.quhealthy.onboarding_service.event.OnboardingEventPublisher;
import com.quhealthy.onboarding_service.model.ProviderOnboarding;
import com.quhealthy.onboarding_service.model.ProviderProfile;
import com.quhealthy.onboarding_service.model.enums.OnboardingStatus;
import com.quhealthy.onboarding_service.repository.ProviderOnboardingRepository;
import com.quhealthy.onboarding_service.repository.ProviderProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileService {

    private final ProviderOnboardingRepository onboardingStatusRepository;
    private final ProviderProfileRepository profileDataRepository;
    private final OnboardingEventPublisher eventPublisher;
    private final Slugify slugify = Slugify.builder().build();

    @Transactional
    public void updateProfile(Long userId, UpdateProfileRequest request) {
        log.info("📝 Actualizando perfil para proveedor ID: {}", userId);

        // 1. UPSERT DATOS CORE (Si no existe, lo crea)
        ProviderProfile profile = profileDataRepository.findById(userId)
                .orElse(ProviderProfile.builder().providerId(userId).build());

        // Mapeo de campos
        profile.setBusinessName(request.getBusinessName());
        profile.setBio(request.getBio());
        profile.setProfileImageUrl(request.getProfileImageUrl());
        profile.setAddress(request.getAddress());
        profile.setLatitude(request.getLatitude());
        profile.setLongitude(request.getLongitude());
        profile.setCategoryId(request.getCategoryId());
        profile.setSubCategoryId(request.getSubCategoryId());

        // ✅ NUEVOS CAMPOS (Que agregamos al DTO y Modelo)
        profile.setWebsiteUrl(request.getWebsiteUrl());
        profile.setContactPhone(request.getContactPhone());

        if (request.getPlaceId() != null) {
            profile.setGooglePlaceId(request.getPlaceId());
        }

        // Generación de Slug único
        if (profile.getSlug() == null || profile.getSlug().isEmpty()) {
            String baseSlug = slugify.slugify(request.getBusinessName());
            // Verificación simple de unicidad agregando ID
            profile.setSlug(baseSlug + "-" + userId);
        }

        profileDataRepository.save(profile);

        // 2. ACTUALIZAR ESTADO
        ProviderOnboarding status = onboardingStatusRepository.findById(userId)
                .orElse(ProviderOnboarding.builder().providerId(userId).build());

        status.setProfileStatus(OnboardingStatus.COMPLETED);

        // Si KYC no ha empezado, aseguramos que esté PENDING (desbloqueado)
        if (status.getKycStatus() == null) {
            status.setKycStatus(OnboardingStatus.PENDING);
        }

        onboardingStatusRepository.save(status);

        // 3. PUBLICAR EVENTO (Notification Service enviará email de bienvenida/avance)
        Map<String, Object> extraData = new HashMap<>();
        extraData.put("businessName", request.getBusinessName());

        eventPublisher.publishStepCompleted(userId, null, "PROFILE", extraData);

        log.info("✅ Perfil completado para ID: {}", userId);
    }
}
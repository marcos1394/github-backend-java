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
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileService {

    private final ProviderOnboardingRepository onboardingStatusRepository;
    private final ProviderProfileRepository profileDataRepository;
    private final OnboardingEventPublisher eventPublisher;

    // Slugify thread-safe instance
    private final Slugify slugify = Slugify.builder().build();

    @Transactional
    public void updateProfile(Long userId, UpdateProfileRequest request) {
        log.info("📝 Actualizando perfil para proveedor ID: {}", userId);

        // 1. UPSERT DATOS CORE
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

        // Campos de contacto adicionales
        profile.setWebsiteUrl(request.getWebsiteUrl());
        profile.setContactPhone(request.getContactPhone());

        if (request.getPlaceId() != null) {
            profile.setGooglePlaceId(request.getPlaceId());
        }

        // 2. GENERACIÓN DE SLUG (Crítico para URLs amigables)
        // Si no tiene slug o cambió el nombre del negocio, regeneramos
        if (profile.getSlug() == null || profile.getSlug().isEmpty()) {
            String baseSlug = slugify.slugify(request.getBusinessName());
            // Agregamos una parte aleatoria corta para garantizar unicidad global sin consultar DB remota
            // Ej: "clinica-dental-sonrisas-a1b2"
            String uniqueSuffix = UUID.randomUUID().toString().substring(0, 4);
            profile.setSlug(baseSlug + "-" + uniqueSuffix);
        }

        profileDataRepository.save(profile);

        // 3. ACTUALIZAR ESTADO LOCAL
        ProviderOnboarding status = onboardingStatusRepository.findById(userId)
                .orElse(ProviderOnboarding.builder().providerId(userId).build());

        status.setProfileStatus(OnboardingStatus.COMPLETED);

        // Si KYC no ha empezado, desbloqueamos el paso
        if (status.getKycStatus() == null) {
            status.setKycStatus(OnboardingStatus.PENDING);
        }

        onboardingStatusRepository.save(status);

        // 4. PUBLICAR EVENTO DE SINCRONIZACIÓN 🔄
        // Enviamos TODOS los datos que el Auth Service necesita replicar en su tabla 'providers'
        Map<String, Object> syncData = new HashMap<>();
        syncData.put("businessName", profile.getBusinessName());
        syncData.put("slug", profile.getSlug());
        syncData.put("bio", profile.getBio());
        syncData.put("profileImageUrl", profile.getProfileImageUrl());
        syncData.put("address", profile.getAddress());
        syncData.put("latitude", profile.getLatitude());
        syncData.put("longitude", profile.getLongitude());
        syncData.put("categoryId", profile.getCategoryId());
        syncData.put("subCategoryId", profile.getSubCategoryId());

        // Este evento dispara la actualización en Auth Service y notifica al usuario
        eventPublisher.publishStepCompleted(userId, null, "PROFILE_COMPLETED", syncData);

        log.info("✅ Perfil completado y evento de sincronización enviado para ID: {}", userId);
    }
}
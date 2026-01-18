package com.quhealthy.auth_service.service;

import com.quhealthy.auth_service.dto.RegisterProviderRequest;
import com.quhealthy.auth_service.model.*;
import com.quhealthy.auth_service.model.enums.*;
import com.quhealthy.auth_service.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.security.SecureRandom;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    // --- Repositorios ---
    private final ProviderRepository providerRepository;
    private final ProviderPlanRepository providerPlanRepository;
    private final ProviderKYCRepository kycRepository;
    private final ProviderLicenseRepository licenseRepository;
    private final ProviderMarketplaceRepository marketplaceRepository;
    private final ReferralRepository referralRepository;
    private final PlanRepository planRepository;

    // --- Servicios Externos ---
    private final PasswordEncoder passwordEncoder; // BCrypt
    private final NotificationService notificationService; // Tu servicio de notificaciones

    // Variable de entorno para construir links (http://localhost:3000 o https://quhealthy.com)
    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    @Transactional
    public Provider registerProvider(RegisterProviderRequest request) {
        log.info("🏁 [AuthService] Iniciando registro para: {}", request.getEmail());

        // 1. Validar duplicados
        if (providerRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("El correo electrónico ya está registrado.");
        }

        // 2. Mapear Categoría (Legacy Logic)
        Long parentCategoryId = "health".equalsIgnoreCase(request.getServiceType()) ? 1L : 2L;

        // 3. Manejo de Referidos
        Provider referrer = null;
        if (request.getReferralCode() != null && !request.getReferralCode().isBlank()) {
            referrer = providerRepository.findByReferralCode(request.getReferralCode()).orElse(null);
        }

        // 4. Generar Tokens de Verificación
        String emailToken = UUID.randomUUID().toString();
        String phoneToken = String.valueOf(100000 + new SecureRandom().nextInt(900000));
        LocalDateTime tokenExpiry = LocalDateTime.now().plusHours(24);

        // 5. Crear Entidad Provider
        Provider provider = new Provider();
        provider.setName(request.getName());
        provider.setEmail(request.getEmail());
        provider.setPhone(request.getPhone());
        provider.setPassword(passwordEncoder.encode(request.getPassword())); // BCrypt Hash
        provider.setRole(Role.PROVIDER);
        provider.setParentCategoryId(parentCategoryId);
        provider.setAcceptTerms(request.isAcceptTerms());
        
        // Configuración Inicial
        provider.setPlanStatus(PlanStatus.TRIAL);
        provider.setTrialExpiresAt(LocalDateTime.now().plusDays(14));
        provider.setOnboardingComplete(false);
        provider.setReferralCode(Long.toHexString(Double.doubleToLongBits(Math.random())).substring(0, 8).toUpperCase());
        
        // Datos de Verificación
        provider.setEmailVerified(false);
        provider.setEmailVerificationToken(emailToken);
        provider.setEmailVerificationExpires(tokenExpiry);
        provider.setPhoneVerified(false);
        provider.setPhoneVerificationToken(phoneToken);
        provider.setPhoneVerificationExpires(LocalDateTime.now().plusMinutes(10));

        if (referrer != null) {
            provider.setReferredById(Math.toIntExact(referrer.getId()));
        }

        // Guardar Provider (Para generar ID)
        provider = providerRepository.save(provider);
        log.info("💾 Provider creado con ID: {}", provider.getId());

        // 6. Crear Registros Satélite (Cascada)

        // A. Referral Record
        if (referrer != null) {
            Referral referral = new Referral();
            referral.setReferrer(referrer);
            referral.setReferee(provider);
            referral.setStatus(ReferralStatus.PENDING);
            referralRepository.save(referral);
        }

        // B. Plan Gratuito (ID 5)
        Plan freePlan = planRepository.findById(5L)
                .orElseThrow(() -> new RuntimeException("Error Crítico: El Plan Base (ID 5) no existe en BD. Ejecuta los seeds."));
        
        ProviderPlan plan = new ProviderPlan();
        plan.setProvider(provider);
        plan.setPlan(freePlan);
        plan.setStatus(PlanStatus.TRIAL);
        plan.setStartDate(LocalDateTime.now());
        plan.setEndDate(provider.getTrialExpiresAt());
        providerPlanRepository.save(plan);

        // C. KYC & License Vacíos
        ProviderKYC kyc = new ProviderKYC();
        kyc.setProvider(provider);
        kyc.setKycStatus(KYCStatus.NOT_STARTED);
        kycRepository.save(kyc);

        ProviderLicense license = new ProviderLicense();
        license.setProvider(provider);
        license.setStatus(LicenseStatus.PENDING);
        licenseRepository.save(license);

        // D. Marketplace (Tienda)
        ProviderMarketplace shop = new ProviderMarketplace();
        shop.setProvider(provider);
        shop.setStoreName("Tienda de " + provider.getName());
        shop.setStoreSlug("tienda-" + provider.getId() + "-" + System.currentTimeMillis());
        marketplaceRepository.save(shop);

        // 7. ENVIAR NOTIFICACIONES (Usando tu nuevo servicio)
        try {
            // Construir Link: http://localhost:3000/verify-email?token=...
            String link = frontendUrl + "/verify-email?token=" + emailToken + "&role=provider";
            
            // Email (Resend + Thymeleaf)
            notificationService.sendVerificationEmail(provider.getEmail(), provider.getName(), link);
            
            // SMS (Twilio)
            if (provider.getPhone() != null) {
                notificationService.sendVerificationSms(provider.getPhone(), phoneToken);
            }
            
            log.info("📧 Notificaciones de verificación enviadas.");

        } catch (Exception e) {
            // No hacemos rollback si falla el email, pero logueamos el error grave
            log.error("⚠️ Usuario creado pero falló el envío de notificaciones: {}", e.getMessage());
        }

        return provider;
    }

    // ========================================================================
    // 2. VERIFICACIÓN DE EMAIL (¡ESTO FALTABA!)
    // ========================================================================
    public String verifyEmail(String token) {
        Provider provider = providerRepository.findByEmailVerificationToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Token inválido o expirado."));

        if (provider.isEmailVerified()) {
            return "El correo ya ha sido verificado anteriormente.";
        }

        if (provider.getEmailVerificationExpires().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("El enlace de verificación ha expirado. Por favor solicita uno nuevo.");
        }

        // Activar
        provider.setEmailVerified(true);
        provider.setEmailVerificationToken(null); // Invalidar token por seguridad
        provider.setEmailVerificationExpires(null);
        
        providerRepository.save(provider);
        
        log.info("✅ Email verificado para provider ID: {}", provider.getId());
        
        return "Correo verificado exitosamente.";
    }

}
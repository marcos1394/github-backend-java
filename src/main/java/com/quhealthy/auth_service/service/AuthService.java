package com.quhealthy.auth_service.service;

import com.quhealthy.auth_service.dto.RegisterProviderRequest;
import com.quhealthy.auth_service.model.*;
import com.quhealthy.auth_service.model.enums.*;
import com.quhealthy.auth_service.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;
import java.security.SecureRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    // Inyección de Repositorios (Acceso a Datos)
    private final ProviderRepository providerRepository;
    private final ProviderPlanRepository providerPlanRepository;
    private final ProviderKYCRepository kycRepository;
    private final ProviderLicenseRepository licenseRepository;
    private final ProviderMarketplaceRepository marketplaceRepository;
    private final ReferralRepository referralRepository;
    private final PlanRepository planRepository;

    // Inyección de Herramientas
    private final PasswordEncoder passwordEncoder; // BCrypt
    private final NotificationService notificationService; // Email & SMS

    /**
     * Registra un nuevo proveedor médico en el ecosistema QuHealthy.
     * Crea automáticamente: Perfil, Plan Trial, KYC vacío, Licencia pendiente y Tienda.
     */
    @Transactional
    public Provider registerProvider(RegisterProviderRequest request) {
        log.info("🏁 [AuthService] Iniciando transacción para registro de: {}", request.getEmail());

        // 1. VALIDACIÓN PREVIA: Verificar si el Email ya existe
        if (providerRepository.existsByEmail(request.getEmail())) {
            log.warn("⚠️ Intento de registro con email duplicado: {}", request.getEmail());
            throw new RuntimeException("El correo electrónico ya está registrado.");
        }

        // 2. LÓGICA DE NEGOCIO: Determinar Categoría Padre
        // 1=Salud, 2=Belleza (Esto podría venir de una config, pero lo hardcodeamos por consistencia con tu lógica)
        Long parentCategoryId = "health".equalsIgnoreCase(request.getServiceType()) ? 1L : 2L;

        // 3. LÓGICA DE REFERIDOS: ¿Viene invitado por alguien?
        Provider referrer = null;
        if (request.getReferralCode() != null && !request.getReferralCode().isBlank()) {
            referrer = providerRepository.findByReferralCode(request.getReferralCode())
                    .orElse(null);
            if (referrer != null) {
                log.info("🤝 [AuthService] Usuario referido por Provider ID: {}", referrer.getId());
            } else {
                log.warn("⚠️ Código de referido inválido: {}", request.getReferralCode());
                // No detenemos el registro, simplemente ignoramos el código inválido
            }
        }

        // 4. PREPARAR TOKENS DE VERIFICACIÓN
        String emailToken = UUID.randomUUID().toString();
        LocalDateTime emailExpire = LocalDateTime.now().plusHours(24);
        
        // Generar código numérico de 6 dígitos para SMS
        String phoneToken = String.valueOf(100000 + new SecureRandom().nextInt(900000));
        LocalDateTime phoneExpire = LocalDateTime.now().plusMinutes(10);

        // 5. CONSTRUIR ENTIDAD PROVIDER
        Provider provider = new Provider();
        provider.setName(request.getName());
        provider.setEmail(request.getEmail());
        provider.setPhone(request.getPhone());
        provider.setPassword(passwordEncoder.encode(request.getPassword())); // Hasheado con BCrypt
        provider.setRole(Role.PROVIDER);
        provider.setParentCategoryId(parentCategoryId);
        provider.setAcceptTerms(request.isAcceptTerms());
        
        // Configuración de Estado Inicial
        provider.setPlanStatus(PlanStatus.TRIAL);
        provider.setTrialExpiresAt(LocalDateTime.now().plusDays(14)); // 14 días de prueba
        provider.setOnboardingComplete(false);
        
        // Generar su propio código de referido único (Hex de 8 chars)
        provider.setReferralCode(Long.toHexString(Double.doubleToLongBits(Math.random())).substring(0, 8).toUpperCase());
        
        if (referrer != null) {
            provider.setReferredById(Math.toIntExact(referrer.getId()));
        }

        // Configuración de Verificación
        provider.setEmailVerified(false);
        provider.setEmailVerificationToken(emailToken);
        provider.setEmailVerificationExpires(emailExpire);
        provider.setPhoneVerified(false);
        provider.setPhoneVerificationToken(phoneToken);
        provider.setPhoneVerificationExpires(phoneExpire);

        // --- GUARDAR PROVIDER (Paso Crítico 1) ---
        provider = providerRepository.save(provider);
        log.info("✅ [AuthService] Provider guardado con ID: {}", provider.getId());

        // 6. CREAR REGISTRO DE REFERIDO (Si aplica)
        if (referrer != null) {
            Referral referral = new Referral();
            referral.setReferrer(referrer);
            referral.setReferee(provider);
            referral.setStatus(ReferralStatus.PENDING);
            referralRepository.save(referral);
        }

        // 7. ASIGNAR PLAN GRATUITO/TRIAL (ID 5)
        Plan freePlan = planRepository.findById(5L)
                .orElseThrow(() -> new RuntimeException("Error crítico: El Plan Base (ID 5) no existe en la base de datos."));
        
        ProviderPlan providerPlan = new ProviderPlan();
        providerPlan.setProvider(provider);
        providerPlan.setPlan(freePlan);
        providerPlan.setStatus(PlanStatus.TRIAL);
        providerPlan.setStartDate(LocalDateTime.now());
        providerPlan.setEndDate(provider.getTrialExpiresAt());
        providerPlanRepository.save(providerPlan);

        // 8. INICIALIZAR REGISTROS SATÉLITE (KYC, Licencia, Tienda)
        
        // KYC Vacío
        ProviderKYC kyc = new ProviderKYC();
        kyc.setProvider(provider);
        kyc.setKycStatus(KYCStatus.NOT_STARTED);
        kycRepository.save(kyc);

        // Licencia Pendiente
        ProviderLicense license = new ProviderLicense();
        license.setProvider(provider);
        license.setStatus(LicenseStatus.PENDING);
        // license.setFullName(provider.getName()); // Opcional: pre-llenar nombre
        licenseRepository.save(license);

        // Marketplace (Tienda)
        ProviderMarketplace marketplace = new ProviderMarketplace();
        marketplace.setProvider(provider);
        marketplace.setStoreName("Tienda de " + provider.getName());
        // Generar Slug único: tienda-ID-timestamp
        String initialSlug = "tienda-" + provider.getId() + "-" + System.currentTimeMillis();
        marketplace.setStoreSlug(initialSlug);
        marketplaceRepository.save(marketplace);

        log.info("✅ [AuthService] Registros satélite (KYC, Licencia, Tienda) creados exitosamente.");

        // 9. ENVIAR NOTIFICACIONES (Email & SMS)
        // Nota: En producción, esto debería ir a una cola de mensajes (RabbitMQ/Kafka) para no bloquear.
        // Aquí lo hacemos directo pero protegido con try-catch para no romper el registro si falla el email.
        
        try {
            // Construir enlace (Asegúrate de configurar FRONTEND_URL en application.properties o variables de entorno)
            String frontendUrl = System.getenv("FRONTEND_URL") != null ? System.getenv("FRONTEND_URL") : "https://quhealthy.com";
            String verifyLink = frontendUrl + "/verify-email?token=" + emailToken + "&role=provider";
            
            log.info("📧 [AuthService] Enviando correo de verificación a: {}", provider.getEmail());
            notificationService.sendVerificationEmail(provider.getEmail(), provider.getName(), verifyLink);
            
            // Enviamos copia a tu correo de admin (según tu lógica anterior)
            // notificationService.sendVerificationEmail("marcosbancaprepa@gmail.com", "Admin Monitor", verifyLink);
            
        } catch (Exception e) {
            log.error("🚨 Error no bloqueante enviando EMAIL: {}", e.getMessage());
        }

        try {
            if (provider.getPhone() != null && !provider.getPhone().isBlank()) {
                log.info("📱 [AuthService] Enviando SMS de verificación a: {}", provider.getPhone());
                notificationService.sendVerificationSms(provider.getPhone(), phoneToken);
            }
        } catch (Exception e) {
            log.error("🚨 Error no bloqueante enviando SMS: {}", e.getMessage());
        }

        return provider;
    }
}
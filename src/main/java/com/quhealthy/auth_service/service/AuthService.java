package com.quhealthy.auth_service.service;

import com.quhealthy.auth_service.dto.AuthResponse;
import com.quhealthy.auth_service.dto.ForgotPasswordRequest;
import com.quhealthy.auth_service.dto.LoginRequest;
import com.quhealthy.auth_service.dto.ProviderStatusResponse;
import com.quhealthy.auth_service.dto.RegisterProviderRequest;
import com.quhealthy.auth_service.dto.ResetPasswordRequest;
import com.quhealthy.auth_service.model.*;
import com.quhealthy.auth_service.model.enums.*;
// Inyectar esto
import com.quhealthy.auth_service.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.quhealthy.auth_service.service.security.JwtService; // Importamos el nuevo servicio
import java.time.LocalDateTime;
import java.security.SecureRandom;
import java.util.HexFormat;
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
    private final JwtService jwtService;
    private final ProviderCourseRepository courseRepository;

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

    // ========================================================================
    // 3. LOGIN (AUTENTICACIÓN)
    // ========================================================================
    @Transactional(readOnly = true) // Solo lectura para login es más eficiente
    public AuthResponse login(LoginRequest request) {
        log.info("🔐 [AuthService] Iniciando login para: {}", request.getIdentifier());

        // 1. Buscar al proveedor por email O teléfono
        Provider provider = providerRepository.findByEmailOrPhone(request.getIdentifier())
                .orElseThrow(() -> new IllegalArgumentException("Credenciales incorrectas.")); // Mensaje genérico por seguridad

        // 2. Verificar la contraseña (BCrypt)
        if (!passwordEncoder.matches(request.getPassword(), provider.getPassword())) {
            log.warn("⚠️ Login fallido: Contraseña incorrecta para {}", request.getIdentifier());
            throw new IllegalArgumentException("Credenciales incorrectas.");
        }

        // 3. Verificar si el email O el teléfono están verificados
        if (!provider.isEmailVerified() && !provider.isPhoneVerified()) {
            log.warn("⚠️ Login bloqueado: Usuario no verificado.");
            throw new IllegalArgumentException("Por favor, verifica tu correo o teléfono para iniciar sesión.");
        }

        // --- LÓGICA 2FA ---
        // 4. Verificar si 2FA está activado
        if (Boolean.TRUE.equals(provider.getIsTwoFactorEnabled())) { // Boolean safe check
            log.info("🛡️ 2FA activado para usuario ID: {}. Generando token parcial.", provider.getId());

            String partialToken = jwtService.generatePartialToken(provider.getId(), provider.getRole().name());

            // Devolvemos respuesta de "2FA Requerido"
            return AuthResponse.builder()
                    .token(null)
                    .partialToken(partialToken)
                    .message("Se requiere autenticación de dos factores.")
                    .status(AuthResponse.AuthStatus.builder()
                            .twoFactorRequired(true)
                            .build())
                    .build();
        }

        // 5. Si 2FA NO está activado, proceder con Login Normal
        log.info("✅ Login exitoso. Generando sesión completa.");

        // Validar Plan Activo (Lógica equivalente a tu .find en JS)
        // Buscamos si tiene algún plan que NO haya expirado y que esté activo/trial
        boolean hasActivePlan = false;
        if (provider.getPlans() != null) {
            hasActivePlan = provider.getPlans().stream().anyMatch(sub -> 
                (sub.getStatus() == PlanStatus.ACTIVE || sub.getStatus() == PlanStatus.TRIAL) &&
                sub.getEndDate().isAfter(LocalDateTime.now())
            );
        }

        // Generar Token Completo
        String jwtToken = jwtService.generateToken(
            provider.getId(), 
            provider.getEmail(), 
            provider.getRole().name()
        );

        return AuthResponse.builder()
                .token(jwtToken)
                .partialToken(null)
                .message("Inicio de sesión exitoso.")
                .status(AuthResponse.AuthStatus.builder()
                        .hasActivePlan(hasActivePlan)
                        .onboardingComplete(provider.isOnboardingComplete())
                        .isEmailVerified(provider.isEmailVerified())
                        .isPhoneVerified(provider.isPhoneVerified())
                        .twoFactorRequired(false)
                        .build())
                .build();
    }


    // ========================================================================
    // 4. SOLICITAR RESETEO (FORGOT PASSWORD)
    // ========================================================================
    @Transactional
    public void requestPasswordReset(ForgotPasswordRequest request) {
        log.info("🚀 [AuthService] Solicitud de reseteo para: {}", request.getEmail());

        // 1. Buscar usuario (Si no existe, no hacemos nada por seguridad - User Enumeration Prevention)
        Provider provider = providerRepository.findByEmail(request.getEmail()).orElse(null);

        if (provider != null) {
            // 2. Generar Selector (16 bytes) y Verifier (32 bytes)
            SecureRandom random = new SecureRandom();
            byte[] selectorBytes = new byte[16];
            byte[] verifierBytes = new byte[32];
            random.nextBytes(selectorBytes);
            random.nextBytes(verifierBytes);

            // Convertir a Hex (Equivalente a .toString('hex') de Node)
            String selector = HexFormat.of().formatHex(selectorBytes);
            String verifier = HexFormat.of().formatHex(verifierBytes);

            // 3. Hashear el Verifier (Usamos passwordEncoder que ya es BCrypt)
            String verifierHash = passwordEncoder.encode(verifier);

            // 4. Guardar en BD
            provider.setResetSelector(selector);
            provider.setResetVerifierHash(verifierHash);
            provider.setResetTokenExpiresAt(LocalDateTime.now().plusMinutes(60)); // 1 hora
            providerRepository.save(provider);

            // 5. Construir Link y Enviar Correo
            // Link: https://quhealthy.com/reset-password?selector=...&verifier=...&role=provider
            String link = String.format("%s/reset-password?selector=%s&verifier=%s&role=provider", 
                    frontendUrl, selector, verifier);

            // Usamos la plantilla 'password-reset-request' que ya tienes
            notificationService.sendPasswordResetRequest(provider.getEmail(), link);
            
            log.info("✅ Tokens generados y correo enviado a {}", request.getEmail());
        } else {
            log.warn("ℹ️ Email no encontrado: {}. Silenciando respuesta.", request.getEmail());
        }
    }

    // ========================================================================
    // 5. CAMBIAR CONTRASEÑA (RESET PASSWORD)
    // ========================================================================
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        log.info("🔄 [AuthService] Intentando restablecer password con selector: {}", request.getSelector());

        // 1. Buscar por Selector y Validar Expiración
        // Nota: Deberíamos tener un método findByResetSelector en el repo, pero podemos usar un filter rápido o query
        // Para hacerlo limpio, agregaremos el método al Repository abajo.
        Provider provider = providerRepository.findByResetSelector(request.getSelector())
                .orElseThrow(() -> new IllegalArgumentException("El enlace es inválido o ha expirado."));

        // 2. Validar Expiración Temporal
        if (provider.getResetTokenExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("El enlace ha expirado. Solicita uno nuevo.");
        }

        // 3. Validar el Verifier contra el Hash almacenado
        if (!passwordEncoder.matches(request.getVerifier(), provider.getResetVerifierHash())) {
            throw new IllegalArgumentException("Enlace inválido (Verificación fallida).");
        }

        // 4. Actualizar Password
        provider.setPassword(passwordEncoder.encode(request.getNewPassword()));

        // 5. Limpiar Tokens (Single Use)
        provider.setResetSelector(null);
        provider.setResetVerifierHash(null);
        provider.setResetTokenExpiresAt(null);
        
        providerRepository.save(provider);

        // 6. Enviar Notificación de Seguridad (Plantilla 'password-changed')
        String time = LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
        notificationService.sendPasswordChangedAlert(provider.getEmail(), provider.getName(), time, "Navegador Web");

        log.info("✅ Contraseña actualizada exitosamente para ID: {}", provider.getId());
    }

    @Transactional(readOnly = true)
    public ProviderStatusResponse getProviderStatus(Long providerId) {
        log.info("🔹 [AuthService] Obteniendo estado para Provider ID: {}", providerId);

        // 1. Obtener Provider con sus relaciones clave
        // Nota: Al usar JPA y tener las relaciones en el modelo, basta con buscar al provider.
        // Hibernate hará los JOINS o Selects necesarios eficientemente si están configurados como FetchType.LAZY (por defecto)
        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new IllegalArgumentException("Proveedor no encontrado."));

        // 2. Obtener datos satélite (Manejo de nulls seguro)
        ProviderKYC kyc = kycRepository.findByProviderId(providerId).orElse(null);
        ProviderLicense license = licenseRepository.findByProviderId(providerId).orElse(null);
        
        // Contar cursos
        long courseCount = courseRepository.countByProviderId(providerId);

        // 3. Buscar Plan Activo
        // Filtramos en memoria la lista de planes del provider para encontrar el vigente
        // Esto evita una query extra si ya trajimos los planes, o hace una query ligera si es Lazy.
        ProviderPlan activePlanData = null;
        if (provider.getPlans() != null) {
            activePlanData = provider.getPlans().stream()
                .filter(p -> (p.getStatus() == PlanStatus.ACTIVE || p.getStatus() == PlanStatus.TRIAL))
                .filter(p -> p.getEndDate().isAfter(LocalDateTime.now()))
                .findFirst()
                .orElse(null);
        }

        // 4. Determinar Estados (Lógica de Negocio)
        boolean isKycComplete = provider.isKycVerified() || (kyc != null && (kyc.getKycStatus() == KYCStatus.APPROVED || kyc.getKycStatus() == KYCStatus.APPROVED));
        
        boolean isLicenseRequired = provider.getParentCategoryId() == 1L; // 1 = Salud
        boolean isLicenseComplete = provider.isLicenseVerified() || (license != null && license.getStatus() == LicenseStatus.VERIFIED);
        
        boolean isMarketplaceSetupComplete = provider.isMarketplaceConfigured();
        boolean isBlocksSetupComplete = courseCount > 0;

        // 5. Construir Detalles del Plan (Mapping)
        Plan planRef = (activePlanData != null) ? activePlanData.getPlan() : null;

        ProviderStatusResponse.Permissions permissions = ProviderStatusResponse.Permissions.builder()
                .quMarketAccess(planRef != null && planRef.isQumarketAccess())
                .quBlocksAccess(planRef != null && planRef.isQublocksAccess())
                .marketingLevel(planRef != null ? planRef.getMarketingLevel() : 0)
                .supportLevel(planRef != null ? planRef.getSupportLevel() : 0)
                .advancedReports(planRef != null && planRef.isAdvancedReports())
                .userManagement(planRef != null ? planRef.getUserManagement() : 0)
                .allowAdvancePayments(planRef != null && planRef.isAllowAdvancePayments())
                // Manejo de "Ilimitados" vs Números
                .maxAppointments(planRef != null ? (planRef.getMaxAppointments() == -1 ? "Ilimitados" : String.valueOf(planRef.getMaxAppointments())) : "0")
                .maxProducts(planRef != null ? (planRef.getMaxProducts() == -1 ? "Ilimitados" : String.valueOf(planRef.getMaxProducts())) : "0")
                .maxCourses(planRef != null ? (planRef.getMaxCourses() == -1 ? "Ilimitados" : String.valueOf(planRef.getMaxCourses())) : "0")
                .build();

        ProviderStatusResponse.PlanDetails planDetails = ProviderStatusResponse.PlanDetails.builder()
                .planId(planRef != null ? planRef.getId() : null)
                .planName(planRef != null ? planRef.getName() : "Sin plan activo")
                .hasActivePlan(activePlanData != null)
                .planStatus(provider.getPlanStatus().name()) // Enum a String
                .endDate(activePlanData != null ? activePlanData.getEndDate() : 
                        (provider.getPlanStatus() == PlanStatus.TRIAL ? provider.getTrialExpiresAt() : null))
                .permissions(permissions)
                .build();

        // 6. Construir Respuesta Final
        return ProviderStatusResponse.builder()
                .onboardingStatus(ProviderStatusResponse.OnboardingStatus.builder()
                        .kyc(ProviderStatusResponse.StatusDetail.builder()
                                .status(kyc != null ? kyc.getKycStatus().name() : "NOT_STARTED")
                                .isComplete(isKycComplete)
                                .build())
                        .license(ProviderStatusResponse.LicenseDetail.builder()
                                .isRequired(isLicenseRequired)
                                .status(license != null ? license.getStatus().name() : "PENDING")
                                .isComplete(isLicenseComplete)
                                .build())
                        .marketplace(ProviderStatusResponse.ConfigDetail.builder()
                                .isConfigured(isMarketplaceSetupComplete)
                                .build())
                        .blocks(ProviderStatusResponse.ConfigDetail.builder()
                                .isConfigured(isBlocksSetupComplete)
                                .build())
                        .build())
                .planDetails(planDetails)
                .providerDetails(ProviderStatusResponse.ProviderDetails.builder()
                        .parentCategoryId(provider.getParentCategoryId())
                        .email(provider.getEmail())
                        .name(provider.getName())
                        .archetype(provider.getArchetype() != null ? provider.getArchetype().name() : null)
                        .stripeAccountId(provider.getStripeAccountId())
                        .build())
                .build();
    }


    // ========================================================================
    // 6. MÉTODOS DE CONSULTA Y HELPERS (Queries)
    // ========================================================================

    /**
     * Helper para buscar un proveedor por email.
     * Utilizado por AuthController para obtener el contexto del usuario actual (/me).
     */
    @Transactional(readOnly = true) // Importante: readOnly optimiza la consulta
    public Provider findByEmail(String email) {
        return providerRepository.findByEmail(email).orElse(null);
    }


}
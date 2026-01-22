package com.quhealthy.auth_service.service;

import com.quhealthy.auth_service.dto.AuthResponse;
import com.quhealthy.auth_service.dto.ConsumerStatusResponse;
import com.quhealthy.auth_service.dto.ForgotPasswordRequest;
import com.quhealthy.auth_service.dto.LoginRequest;
import com.quhealthy.auth_service.dto.ProviderStatusResponse;
import com.quhealthy.auth_service.dto.RegisterConsumerRequest;
import com.quhealthy.auth_service.dto.RegisterProviderRequest;
import com.quhealthy.auth_service.dto.ResendVerificationRequest;
import com.quhealthy.auth_service.dto.ResetPasswordRequest;
import com.quhealthy.auth_service.dto.UserContextResponse;
import com.quhealthy.auth_service.model.*;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
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
import com.quhealthy.auth_service.service.security.GoogleAuthService;
import com.quhealthy.auth_service.dto.SocialLoginRequest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Random;
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
    private final ConsumerRepository consumerRepository;
    private final AuthenticationManager authenticationManager;
    // --- Servicios Externos ---
    private final PasswordEncoder passwordEncoder; // BCrypt
    private final NotificationService notificationService; // Tu servicio de notificaciones
    private final GoogleAuthService googleAuthService; // 💉 Inyectado para Google Login
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
    // 2. VERIFICACIÓN DE EMAIL (POLIMÓRFICO: PROVIDER Y CONSUMER)
    // ========================================================================
    public String verifyEmail(String token) {
        
        // --------------------------------------------------------------------
        // CASO A: BUSCAR EN PROVIDERS
        // --------------------------------------------------------------------
        var providerOpt = providerRepository.findByEmailVerificationToken(token);
        
        if (providerOpt.isPresent()) {
            Provider provider = providerOpt.get();

            if (provider.isEmailVerified()) {
                return "El correo ya ha sido verificado anteriormente.";
            }
            
            // Validar expiración (Ajusta el nombre del getter si es diferente en tu entidad)
            if (provider.getEmailVerificationExpires() != null && 
                provider.getEmailVerificationExpires().isBefore(LocalDateTime.now())) {
                throw new IllegalArgumentException("El enlace de verificación ha expirado.");
            }

            // Activar Provider
            provider.setEmailVerified(true);
            provider.setEmailVerificationToken(null);
            provider.setEmailVerificationExpires(null);
            providerRepository.save(provider);
            
            log.info("✅ Email verificado para Provider ID: {}", provider.getId());
            return "Correo de proveedor verificado exitosamente.";
        }

        // --------------------------------------------------------------------
        // CASO B: BUSCAR EN CONSUMERS (¡NUEVO!)
        // --------------------------------------------------------------------
        var consumerOpt = consumerRepository.findByEmailVerificationToken(token); // Requiere el cambio en el Repo
        
        if (consumerOpt.isPresent()) {
            Consumer consumer = consumerOpt.get();

            if (consumer.isEmailVerified()) {
                return "El correo ya ha sido verificado anteriormente.";
            }

            // Validar expiración
            if (consumer.getEmailVerificationExpires() != null && 
                consumer.getEmailVerificationExpires().isBefore(LocalDateTime.now())) {
                throw new IllegalArgumentException("El enlace de verificación ha expirado.");
            }

            // Activar Consumer
            consumer.setEmailVerified(true);
            consumer.setEmailVerificationToken(null);
            consumer.setEmailVerificationExpires(null); // Asegúrate de que el getter se llame así en Consumer.java
            consumerRepository.save(consumer);
            
            log.info("✅ Email verificado para Consumer ID: {}", consumer.getId());
            return "Correo de paciente verificado exitosamente.";
        }

        // --------------------------------------------------------------------
        // CASO C: NO ENCONTRADO
        // --------------------------------------------------------------------
        throw new IllegalArgumentException("Token inválido o expirado.");
    }


    
    // ========================================================================
    // 3. LOGIN (POLIMÓRFICO: PROVIDER O CONSUMER)
    // ========================================================================
    public AuthResponse login(LoginRequest request) {
        log.info("🔐 [AuthService] Iniciando login para: {}", request.getIdentifier());

        // 1. AUTENTICACIÓN UNIFICADA (Spring Security)
        // Esto usa tu ApplicationConfig para buscar en Provider Y Consumer automáticamente.
        try {
            authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                    request.getIdentifier(),
                    request.getPassword()
                )
            );
        } catch (AuthenticationException e) {
            log.warn("⚠️ Login fallido: Credenciales inválidas para {}", request.getIdentifier());
            throw new IllegalArgumentException("Credenciales incorrectas.");
        }

        // 2. SI LLEGAMOS AQUÍ, LA CONTRASEÑA ES CORRECTA ✅
        // Ahora debemos identificar QUIÉN es el usuario para armar su respuesta específica.

        // --- CASO A: ES UN PROVEEDOR ---
        var providerOpt = providerRepository.findByEmail(request.getIdentifier());
        if (providerOpt.isPresent()) {
            Provider provider = providerOpt.get();

            // A.1 Validar Verificación (Email/Teléfono)
            if (!provider.isEmailVerified() && !provider.isPhoneVerified()) {
                throw new IllegalArgumentException("Por favor, verifica tu correo o teléfono para iniciar sesión.");
            }

            // A.2 Lógica 2FA (Tal cual la tenías)
            if (Boolean.TRUE.equals(provider.getIsTwoFactorEnabled())) {
                log.info("🛡️ 2FA activado para Provider ID: {}", provider.getId());
                String partialToken = jwtService.generatePartialToken(provider.getId(), provider.getRole().name());
                
                return AuthResponse.builder()
                        .partialToken(partialToken)
                        .message("Se requiere autenticación de dos factores.")
                        .status(AuthResponse.AuthStatus.builder().twoFactorRequired(true).build())
                        .build();
            }

            // A.3 Lógica de Planes (Tal cual la tenías)
            boolean hasActivePlan = false;
            if (provider.getPlans() != null) {
                hasActivePlan = provider.getPlans().stream().anyMatch(sub -> 
                    (sub.getStatus() == PlanStatus.ACTIVE || sub.getStatus() == PlanStatus.TRIAL) &&
                    sub.getEndDate().isAfter(LocalDateTime.now())
                );
            }

            // A.4 Generar Token Provider
            String jwtToken = jwtService.generateToken(provider.getId(), provider.getEmail(), provider.getRole().name());

            return AuthResponse.builder()
                    .token(jwtToken)
                    .message("Inicio de sesión exitoso (Provider).")
                    .status(AuthResponse.AuthStatus.builder()
                            .hasActivePlan(hasActivePlan)
                            .onboardingComplete(provider.isOnboardingComplete())
                            .isEmailVerified(provider.isEmailVerified())
                            .isPhoneVerified(provider.isPhoneVerified())
                            .twoFactorRequired(false)
                            .build())
                    .build();
        }

        // --- CASO B: ES UN CONSUMIDOR (NUEVO) ---
        var consumerOpt = consumerRepository.findByEmail(request.getIdentifier());
        if (consumerOpt.isPresent()) {
            Consumer consumer = consumerOpt.get();

            // B.1 Validar Verificación
            if (!consumer.isEmailVerified()) {
                throw new IllegalArgumentException("Por favor, verifica tu correo electrónico.");
            }

            // B.2 Generar Token Consumer
            // Usamos el mismo método generateToken (asegúrate que acepte los argumentos)
            String jwtToken = jwtService.generateToken(consumer.getId(), consumer.getEmail(), consumer.getRole().name());

            // B.3 Respuesta Consumer (Más simple)
            return AuthResponse.builder()
                    .token(jwtToken)
                    .message("Inicio de sesión exitoso (Paciente).")
                    .status(AuthResponse.AuthStatus.builder()
                            .hasActivePlan(false) // Pacientes no suelen tener plan SaaS
                            .onboardingComplete(true) // Asumimos true o checkeamos perfil
                            .isEmailVerified(consumer.isEmailVerified())
                            .isPhoneVerified(false)
                            .twoFactorRequired(false)
                            .build())
                    .build();
        }

        // Si pasó la autenticación pero no lo encontramos en BD (Caso muy raro)
        throw new IllegalArgumentException("Error de inconsistencia de usuario.");
    }


    // ========================================================================
    // 4. SOLICITAR RESETEO (FORGOT PASSWORD) - POLIMÓRFICO
    // ========================================================================
    @Transactional
    public void requestPasswordReset(ForgotPasswordRequest request) {
        log.info("🚀 [AuthService] Solicitud de reseteo para: {}", request.getEmail());

        // Generador seguro (lo instanciamos una vez para usarlo en cualquiera de los dos casos)
        SecureRandom random = new SecureRandom();

        // --------------------------------------------------------------------
        // A. INTENTAR COMO PROVIDER
        // --------------------------------------------------------------------
        var providerOpt = providerRepository.findByEmail(request.getEmail());
        
        if (providerOpt.isPresent()) {
            Provider provider = providerOpt.get();

            // 1. Generar Tokens (Selector + Verifier)
            byte[] selectorBytes = new byte[16];
            byte[] verifierBytes = new byte[32];
            random.nextBytes(selectorBytes);
            random.nextBytes(verifierBytes);

            String selector = HexFormat.of().formatHex(selectorBytes);
            String verifier = HexFormat.of().formatHex(verifierBytes);

            // 2. Hashear y Guardar
            String verifierHash = passwordEncoder.encode(verifier);
            
            provider.setResetSelector(selector);
            provider.setResetVerifierHash(verifierHash);
            provider.setResetTokenExpiresAt(LocalDateTime.now().plusMinutes(60)); // 1 hora
            providerRepository.save(provider);

            // 3. Enviar Correo (Nota el &role=provider)
            // Usa this.frontendUrl cuando tengas el front, por ahora hardcode si quieres probar
            String baseUrl = "https://auth-service-629639328783.us-central1.run.app/api/auth"; // O tu URL de frontend
            
            // En realidad el link debe apuntar a tu FRONTEND, no al backend.
            // Ejemplo: https://quhealthy.com/reset-password?selector=...
            String link = String.format("%s/reset-password?selector=%s&verifier=%s&role=provider", 
                    frontendUrl, selector, verifier);

            notificationService.sendPasswordResetRequest(provider.getEmail(), link);
            log.info("✅ Correo de reseteo enviado a Provider: {}", request.getEmail());
            return;
        }

        // --------------------------------------------------------------------
        // B. INTENTAR COMO CONSUMER (¡NUEVO!)
        // --------------------------------------------------------------------
        var consumerOpt = consumerRepository.findByEmail(request.getEmail());

        if (consumerOpt.isPresent()) {
            Consumer consumer = consumerOpt.get();

            // 1. Generar Tokens
            byte[] selectorBytes = new byte[16];
            byte[] verifierBytes = new byte[32];
            random.nextBytes(selectorBytes);
            random.nextBytes(verifierBytes);

            String selector = HexFormat.of().formatHex(selectorBytes);
            String verifier = HexFormat.of().formatHex(verifierBytes);

            // 2. Hashear y Guardar
            // IMPORTANTE: Asegúrate de tener estos campos en tu entidad Consumer y sus setters
            String verifierHash = passwordEncoder.encode(verifier);
            
            consumer.setResetSelector(selector);
            consumer.setResetVerifierHash(verifierHash);
            consumer.setResetTokenExpiresAt(LocalDateTime.now().plusMinutes(60));
            consumerRepository.save(consumer);

            // 3. Enviar Correo (Nota el &role=consumer)
            String link = String.format("%s/reset-password?selector=%s&verifier=%s&role=consumer", 
                    frontendUrl, selector, verifier);

            notificationService.sendPasswordResetRequest(consumer.getEmail(), link);
            log.info("✅ Correo de reseteo enviado a Consumer: {}", request.getEmail());
            return;
        }

        // --------------------------------------------------------------------
        // C. NO ENCONTRADO (SILENCIOSO)
        // --------------------------------------------------------------------
        log.warn("ℹ️ Email no encontrado para reseteo: {}. Silenciando respuesta por seguridad.", request.getEmail());
    }


    // ========================================================================
    // 5. CAMBIAR CONTRASEÑA (RESET PASSWORD) - POLIMÓRFICO
    // ========================================================================
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        log.info("🔄 [AuthService] Intentando restablecer password con selector: {}", request.getSelector());

        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));

        // --------------------------------------------------------------------
        // A. INTENTAR COMO PROVIDER
        // --------------------------------------------------------------------
        var providerOpt = providerRepository.findByResetSelector(request.getSelector());
        
        if (providerOpt.isPresent()) {
            Provider provider = providerOpt.get();

            // A.1 Validar Expiración
            if (provider.getResetTokenExpiresAt() == null || provider.getResetTokenExpiresAt().isBefore(LocalDateTime.now())) {
                throw new IllegalArgumentException("El enlace ha expirado. Solicita uno nuevo.");
            }

            // A.2 Validar Verifier vs Hash
            if (!passwordEncoder.matches(request.getVerifier(), provider.getResetVerifierHash())) {
                throw new IllegalArgumentException("Enlace inválido (Verificación de seguridad fallida).");
            }

            // A.3 Actualizar Password
            provider.setPassword(passwordEncoder.encode(request.getNewPassword()));

            // A.4 Limpiar Tokens (Single Use)
            provider.setResetSelector(null);
            provider.setResetVerifierHash(null);
            provider.setResetTokenExpiresAt(null);
            providerRepository.save(provider);

            // A.5 Notificar
            notificationService.sendPasswordChangedAlert(provider.getEmail(), provider.getName(), time, "Navegador Web");
            
            log.info("✅ Contraseña actualizada (Provider) ID: {}", provider.getId());
            return;
        }

        // --------------------------------------------------------------------
        // B. INTENTAR COMO CONSUMER (¡NUEVO!)
        // --------------------------------------------------------------------
        var consumerOpt = consumerRepository.findByResetSelector(request.getSelector()); // Requiere cambio en Repo
        
        if (consumerOpt.isPresent()) {
            Consumer consumer = consumerOpt.get();

            // B.1 Validar Expiración
            if (consumer.getResetTokenExpiresAt() == null || consumer.getResetTokenExpiresAt().isBefore(LocalDateTime.now())) {
                throw new IllegalArgumentException("El enlace ha expirado. Solicita uno nuevo.");
            }

            // B.2 Validar Verifier vs Hash
            if (!passwordEncoder.matches(request.getVerifier(), consumer.getResetVerifierHash())) {
                throw new IllegalArgumentException("Enlace inválido (Verificación de seguridad fallida).");
            }

            // B.3 Actualizar Password
            consumer.setPassword(passwordEncoder.encode(request.getNewPassword()));

            // B.4 Limpiar Tokens
            consumer.setResetSelector(null);
            consumer.setResetVerifierHash(null);
            consumer.setResetTokenExpiresAt(null);
            consumerRepository.save(consumer);

            // B.5 Notificar
            notificationService.sendPasswordChangedAlert(consumer.getEmail(), consumer.getName(), time, "Navegador Web");

            log.info("✅ Contraseña actualizada (Consumer) ID: {}", consumer.getId());
            return;
        }

        // --------------------------------------------------------------------
        // C. NO ENCONTRADO
        // --------------------------------------------------------------------
        throw new IllegalArgumentException("El enlace es inválido o no existe.");
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
                // ✅ CORRECCIÓN AQUI: Usamos el helper formatLimit
                .maxAppointments(formatLimit(planRef != null ? planRef.getMaxAppointments() : null))
                .maxProducts(formatLimit(planRef != null ? planRef.getMaxProducts() : null))
                .maxCourses(formatLimit(planRef != null ? planRef.getMaxCourses() : null))
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
    // 8. VERIFICAR TELÉFONO (SMS)
    // ========================================================================
    /**
     * Verifica el código SMS enviado por el usuario.
     * @param email Email del usuario autenticado (extraído del Token JWT).
     * @param code Código de 6 dígitos ingresado por el usuario.
     */
    @Transactional
    public void verifyPhone(String email, String code) {
        log.info("📱 [AuthService] Intentando verificar teléfono para: {}", email);

        // 1. Buscar al Proveedor
        // Usamos el email del token para garantizar que verificamos al usuario correcto
        Provider provider = providerRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));

        // 2. Validar si ya está verificado
        // Al ser 'boolean' primitivo en tu modelo, Lombok genera 'isPhoneVerified()'
        if (provider.isPhoneVerified()) {
            log.warn("⚠️ [AuthService] El usuario {} intentó verificar un teléfono ya verificado.", email);
            throw new IllegalStateException("Tu teléfono ya ha sido verificado anteriormente.");
        }

        // 3. Validar que exista una solicitud pendiente
        if (provider.getPhoneVerificationToken() == null) {
            throw new IllegalArgumentException("No hay un proceso de verificación de teléfono activo. Solicita un código nuevo.");
        }

        // 4. Validar Coincidencia del Código
        // Usamos .equals() para comparar Strings de forma segura
        if (!provider.getPhoneVerificationToken().equals(code)) {
            log.warn("❌ [AuthService] Código SMS incorrecto para usuario {}", email);
            throw new IllegalArgumentException("El código de verificación es incorrecto.");
        }

        // 5. Validar Expiración
        // Comparamos si la fecha de expiración es ANTES de "ahora"
        if (provider.getPhoneVerificationExpires().isBefore(LocalDateTime.now())) {
            log.warn("⏰ [AuthService] Código SMS expirado para usuario {}", email);
            throw new IllegalArgumentException("El código ha expirado. Por favor, solicita uno nuevo.");
        }

        // 6. ÉXITO: Actualizar Estado
        provider.setPhoneVerified(true);           // Marcamos como verificado
        provider.setPhoneVerificationToken(null);  // Limpiamos el token por seguridad
        provider.setPhoneVerificationExpires(null); // Limpiamos la expiración

        // Guardamos los cambios en BD
        providerRepository.save(provider);

        log.info("✅ [AuthService] Teléfono verificado exitosamente para Provider ID: {}", provider.getId());
    }

    // ========================================================================
    // 7. REENVIAR VERIFICACIÓN (RESEND) - EMAIL & SMS (POLIMÓRFICO)
    // ========================================================================
    @Transactional
    public void resendVerification(ResendVerificationRequest request) {
        log.info("📩 [AuthService] Solicitud de reenvío ({}) para: {}", request.getType(), request.getEmail());

        // Variable para la URL (Usa la de Cloud Run para tus pruebas actuales)
        String baseUrl = "https://auth-service-629639328783.us-central1.run.app/api/auth"; 
        // Cuando tengas frontend, usa: String baseUrl = this.frontendUrl;

        // --------------------------------------------------------------------
        // 1. INTENTAR BUSCAR COMO PROVIDER
        // --------------------------------------------------------------------
        var providerOpt = providerRepository.findByEmail(request.getEmail());

        if (providerOpt.isPresent()) {
            Provider provider = providerOpt.get();

            // --- CASO 1.A: EMAIL (PROVIDER) ---
            if ("email".equalsIgnoreCase(request.getType())) {
                if (provider.isEmailVerified()) {
                    throw new IllegalStateException("Esta cuenta ya tiene el correo verificado.");
                }

                String newToken = UUID.randomUUID().toString();
                provider.setEmailVerificationToken(newToken);
                provider.setEmailVerificationExpires(LocalDateTime.now().plusHours(24)); // Ajusta si tu setter se llama diferente
                providerRepository.save(provider);

                // Construir Link
                String link = baseUrl + "/verify-email?token=" + newToken;
                notificationService.sendVerificationEmail(provider.getEmail(), provider.getName(), link);
                
                log.info("✅ Correo de verificación reenviado a Provider: {}", provider.getEmail());
                return;
            }
            
            // --- CASO 1.B: TELÉFONO/SMS (PROVIDER) ---
            else if ("phone".equalsIgnoreCase(request.getType())) {
                if (provider.getPhone() == null || provider.getPhone().isEmpty()) {
                    throw new IllegalArgumentException("El usuario no tiene un teléfono registrado.");
                }
                if (provider.isPhoneVerified()) {
                    throw new IllegalStateException("Este teléfono ya está verificado.");
                }

                String smsCode = String.valueOf(new Random().nextInt(900000) + 100000);
                provider.setPhoneVerificationToken(smsCode);
                provider.setPhoneVerificationExpires(LocalDateTime.now().plusMinutes(10));
                providerRepository.save(provider);

                notificationService.sendVerificationSms(provider.getPhone(), smsCode);
                log.info("✅ SMS reenviado a Provider ID: {}", provider.getId());
                return;
            }
        }

        // --------------------------------------------------------------------
        // 2. INTENTAR BUSCAR COMO CONSUMER (¡LO NUEVO!)
        // --------------------------------------------------------------------
        var consumerOpt = consumerRepository.findByEmail(request.getEmail());

        if (consumerOpt.isPresent()) {
            Consumer consumer = consumerOpt.get();

            // --- CASO 2.A: EMAIL (CONSUMER) ---
            if ("email".equalsIgnoreCase(request.getType())) {
                if (consumer.isEmailVerified()) {
                    throw new IllegalStateException("Esta cuenta ya tiene el correo verificado.");
                }

                String newToken = UUID.randomUUID().toString();
                // Ojo: Asegúrate que los setters coincidan con tu Entidad Consumer
                consumer.setEmailVerificationToken(newToken);
                consumer.setEmailVerificationExpires(LocalDateTime.now().plusHours(24)); 
                consumerRepository.save(consumer);

                // Construir Link
                String link = baseUrl + "/verify-email?token=" + newToken;
                notificationService.sendVerificationEmail(consumer.getEmail(), consumer.getName(), link);

                log.info("✅ Correo de verificación reenviado a Consumer: {}", consumer.getEmail());
                return;
            }

            // --- CASO 2.B: TELÉFONO (CONSUMER) ---
            // Si el consumidor no tiene lógica de SMS aún, lanzamos advertencia o implementamos igual que Provider
            else if ("phone".equalsIgnoreCase(request.getType())) {
                 log.warn("⚠️ SMS solicitado para Consumer, pero no implementado aún.");
                 // Aquí puedes copiar la lógica de SMS del Provider si tu entidad Consumer tiene los campos de teléfono.
                 return;
            }
        }

        // --------------------------------------------------------------------
        // 3. NO ENCONTRADO EN NINGUNO
        // --------------------------------------------------------------------
        log.warn("⚠️ [AuthService] Intento de reenvío para email no existente: {}", request.getEmail());
        // Retornamos silenciosamente (200 OK) por seguridad.
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

    // ========================================================================
    // 9. REGISTRO DE CONSUMIDOR (PACIENTE)
    // ========================================================================
    @Transactional
    public Consumer registerConsumer(RegisterConsumerRequest request) {
        log.info("👤 [AuthService] Registrando nuevo consumidor: {}", request.getEmail());

        // 1. Validar duplicados (Revisamos ambas tablas para evitar colisiones si se comparte login)
        if (providerRepository.existsByEmail(request.getEmail()) || 
            consumerRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("El correo electrónico ya está registrado.");
        }

        // 2. Crear Entidad
        Consumer consumer = new Consumer();
        consumer.setName(request.getName());
        consumer.setEmail(request.getEmail());
        consumer.setPassword(passwordEncoder.encode(request.getPassword()));
        consumer.setRole(Role.CONSUMER);
        
        // Configuraciones por defecto
        consumer.setEmailNotificationsEnabled(true);
        consumer.setPreferredLanguage("es");

        // 3. Generar Token de Verificación de Email
        String verificationToken = UUID.randomUUID().toString();
        consumer.setEmailVerificationToken(verificationToken);
        consumer.setEmailVerificationExpires(LocalDateTime.now().plusHours(24));
        consumer.setEmailVerified(false); // Seguridad primero

        // 4. Guardar
        Consumer savedConsumer = consumerRepository.save(consumer);

        // 5. Enviar Correo de Verificación (Reutilizamos la lógica existente)
        String verifyLink = frontendUrl + "/verify-email?token=" + verificationToken;
        notificationService.sendVerificationEmail(savedConsumer.getEmail(), savedConsumer.getName(), verifyLink);

        log.info("✅ Consumidor registrado exitosamente. ID: {}", savedConsumer.getId());
        return savedConsumer;
    }


    // ========================================================================
    // 10. OBTENER CONTEXTO DE USUARIO (PROVIDER O CONSUMER)
    // ========================================================================
    @Transactional(readOnly = true)
    public UserContextResponse getUserContext(String email) {
        
        // A. Intentar buscar como PROVIDER
        var providerOpt = providerRepository.findByEmail(email);
        if (providerOpt.isPresent()) {
            // Reutilizamos tu lógica existente compleja para providers
            ProviderStatusResponse providerStatus = getProviderStatus(providerOpt.get().getId());
            
            return UserContextResponse.builder()
                    .role("PROVIDER")
                    .providerData(providerStatus)
                    .consumerData(null)
                    .build();
        }

        // B. Intentar buscar como CONSUMER
        var consumerOpt = consumerRepository.findByEmail(email); // Asegúrate de tener consumerRepository inyectado
        if (consumerOpt.isPresent()) {
            Consumer consumer = consumerOpt.get();
            
            // Construimos respuesta ligera para Consumer
            ConsumerStatusResponse consumerStatus = ConsumerStatusResponse.builder()
                    .id(consumer.getId())
                    .name(consumer.getName())
                    .email(consumer.getEmail())
                    .role("CONSUMER")
                    .profileImageUrl(consumer.getProfileImageUrl())
                    .preferredLanguage(consumer.getPreferredLanguage())
                    .emailVerified(consumer.isEmailVerified())
                    .phoneVerified(false) // O consumer.isPhoneVerified() si agregas ese campo
                    .build();

            return UserContextResponse.builder()
                    .role("CONSUMER")
                    .providerData(null)
                    .consumerData(consumerStatus)
                    .build();
        }

        // C. No encontrado
        return null;
    }


    // ========================================================================
    // 4. LOGIN CON GOOGLE (NUEVO MÉTODO)
    // ========================================================================
    @Transactional
    public AuthResponse authenticateWithGoogle(SocialLoginRequest request) {
        log.info("🌐 [Google] Procesando login con token OAuth.");

        // 1. Verificar Token con Google (Extrae email de forma segura)
        var payload = googleAuthService.verifyToken(request.getToken());
        String email = payload.getEmail();
        String name = (String) payload.get("name");

        log.info("✅ [Google] Token válido para: {}", email);

        // 2. Buscar si ya existe (Provider o Consumer)
        var existingProvider = providerRepository.findByEmail(email);
        var existingConsumer = consumerRepository.findByEmail(email);

        if (existingProvider.isPresent()) {
            // --- USUARIO EXISTE (LOGIN) ---
            Provider provider = existingProvider.get();
            log.info("👋 Bienvenido de nuevo Provider: {}", provider.getId());
            
            // Generar Token JWT directo (Google ya verificó identidad)
            String jwtToken = jwtService.generateToken(provider.getId(), provider.getEmail(), provider.getRole().name());
            
            return AuthResponse.builder()
                    .token(jwtToken)
                    .message("Inicio de sesión exitoso con Google.")
                    .status(AuthResponse.AuthStatus.builder()
                            .onboardingComplete(provider.isOnboardingComplete())
                            .isEmailVerified(true) // Google verified
                            .build())
                    .build();

        } else if (existingConsumer.isPresent()) {
             // --- CONSUMER EXISTE (LOGIN) ---
             Consumer consumer = existingConsumer.get();
             String jwtToken = jwtService.generateToken(consumer.getId(), consumer.getEmail(), consumer.getRole().name());
             
             return AuthResponse.builder()
                    .token(jwtToken)
                    .message("Inicio de sesión exitoso con Google.")
                    .status(AuthResponse.AuthStatus.builder()
                            .onboardingComplete(true)
                            .isEmailVerified(true)
                            .build())
                    .build();
        } else {
            // --- USUARIO NO EXISTE (REGISTRO AUTOMÁTICO COMO PROVIDER) ---
            // Asumimos que quien llega por la landing principal es un Proveedor.
            log.info("🆕 Creando nuevo Provider desde Google: {}", email);

            Provider provider = new Provider();
            provider.setName(name);
            provider.setEmail(email);
            provider.setPassword(passwordEncoder.encode(UUID.randomUUID().toString())); // Password aleatorio seguro
            provider.setRole(Role.PROVIDER);
            provider.setParentCategoryId(1L); // Default (Health)
            provider.setAcceptTerms(true);
            provider.setPlanStatus(PlanStatus.TRIAL);
            provider.setTrialExpiresAt(LocalDateTime.now().plusDays(14));
            provider.setOnboardingComplete(false);
            provider.setEmailVerified(true); // ¡Importante! Google ya verificó esto
            
            provider = providerRepository.save(provider);

            // Crear registros satélite mínimos (Plan, Marketplace, etc.)
            // Reutilizamos la lógica de crear Plan Trial
            Plan freePlan = planRepository.findById(5L).orElseThrow();
            ProviderPlan plan = new ProviderPlan();
            plan.setProvider(provider);
            plan.setPlan(freePlan);
            plan.setStatus(PlanStatus.TRIAL);
            plan.setStartDate(LocalDateTime.now());
            plan.setEndDate(provider.getTrialExpiresAt());
            providerPlanRepository.save(plan);
            
            ProviderMarketplace shop = new ProviderMarketplace();
            shop.setProvider(provider);
            shop.setStoreName("Tienda de " + name);
            shop.setStoreSlug("tienda-" + provider.getId() + "-" + System.currentTimeMillis());
            marketplaceRepository.save(shop);
            
            // Generar JWT
            String jwtToken = jwtService.generateToken(provider.getId(), provider.getEmail(), provider.getRole().name());

            return AuthResponse.builder()
                    .token(jwtToken)
                    .message("Registro con Google exitoso.")
                    .status(AuthResponse.AuthStatus.builder()
                            .onboardingComplete(false)
                            .isEmailVerified(true)
                            .build())
                    .build();
        }
    }


    /**
     * Helper para formatear límites del plan de forma segura.
     * Convierte NULLs a "0" y -1 a "Ilimitados".
     */
    private String formatLimit(Integer value) {
        if (value == null) {
            return "0";
        }
        if (value == -1) {
            return "Ilimitados";
        }
        return String.valueOf(value);
    }



}
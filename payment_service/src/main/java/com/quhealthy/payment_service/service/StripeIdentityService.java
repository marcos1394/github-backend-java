package com.quhealthy.payment_service.service;

import com.quhealthy.payment_service.model.MerchantAccount;
import com.quhealthy.payment_service.model.PaymentCustomer;
import com.quhealthy.payment_service.repository.MerchantAccountRepository;
import com.quhealthy.payment_service.repository.PaymentCustomerRepository;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Account;
import com.stripe.model.AccountLink;
import com.stripe.model.Customer;
import com.stripe.param.AccountCreateParams;
import com.stripe.param.AccountLinkCreateParams;
import com.stripe.param.CustomerCreateParams;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class StripeIdentityService {

    private final MerchantAccountRepository merchantRepository;
    private final PaymentCustomerRepository customerRepository;

    @Value("${stripe.api-key}")
    private String stripeApiKey;

    @Value("${application.frontend.url}")
    private String frontendUrl;

    @PostConstruct
    public void init() {
        Stripe.apiKey = stripeApiKey;
    }

    // =================================================================
    // 🛍️ ROL: COMPRADOR (CUSTOMER)
    // Se usa cuando alguien va a pagar (Paciente pagando cita, Doctor pagando plan)
    // =================================================================

    /**
     * Obtiene el ID de Stripe Customer. Si no existe, lo crea al vuelo.
     * @param userId ID del usuario en tu sistema (Auth).
     * @param email Email del usuario.
     * @param name Nombre completo.
     * @return El String "cus_XXXXX" listo para usar en el Checkout.
     */
    @Transactional
    public String getOrCreateCustomer(Long userId, String email, String name) {
        return customerRepository.findByUserId(userId)
                .map(PaymentCustomer::getStripeCustomerId)
                .orElseGet(() -> createStripeCustomer(userId, email, name));
    }

    private String createStripeCustomer(Long userId, String email, String name) {
        log.info("💳 Creando identidad de Pagador (Customer) para User ID: {}", userId);
        try {
            // 1. Crear en Stripe
            CustomerCreateParams params = CustomerCreateParams.builder()
                    .setEmail(email)
                    .setName(name)
                    // Metadata vital para cruzar datos si usas Webhooks
                    .setMetadata(Map.of("userId", String.valueOf(userId)))
                    .build();

            Customer customer = Customer.create(params);

            // 2. Guardar en nuestra BD
            PaymentCustomer newCustomer = PaymentCustomer.builder()
                    .userId(userId)
                    .stripeCustomerId(customer.getId())
                    .build();

            customerRepository.save(newCustomer);
            log.info("✅ Customer creado: {}", customer.getId());
            
            return customer.getId();

        } catch (StripeException e) {
            log.error("❌ Error creando Customer Stripe: {}", e.getMessage());
            throw new RuntimeException("Error al registrar cliente de pagos", e);
        }
    }

    // =================================================================
    // 🏦 ROL: RECEPTOR DE DINERO (MERCHANT / CONNECT)
    // Se usa cuando un Doctor o Partner necesita recibir fondos.
    // =================================================================

    /**
     * Obtiene el ID de la cuenta bancaria virtual (Express Account).
     * @throws IllegalStateException Si el usuario no ha iniciado el onboarding.
     */
    @Transactional(readOnly = true)
    public String getMerchantAccountId(Long userId) {
        return merchantRepository.findByUserId(userId)
                .map(MerchantAccount::getStripeAccountId)
                .orElseThrow(() -> new IllegalStateException("El usuario (ID " + userId + ") no tiene cuenta de pagos configurada."));
    }

    /**
     * Genera el Link Mágico para que el usuario suba sus documentos (INE, CLABE, RFC).
     * Si no tiene cuenta Stripe, la crea primero.
     */
    @Transactional
    public String createOnboardingLink(Long userId, String email) {
        log.info("🏦 Generando Link de Onboarding para User ID: {}", userId);
        
        try {
            // 1. Obtener o Crear la Cuenta Express
            MerchantAccount merchant = merchantRepository.findByUserId(userId)
                    .orElseGet(() -> createMerchantAccount(userId, email));

            // 2. Generar el Link de Stripe Hosted Onboarding
            AccountLinkCreateParams params = AccountLinkCreateParams.builder()
                    .setAccount(merchant.getStripeAccountId())
                    .setRefreshUrl(frontendUrl + "/profile/settings/payments") // Si el link expira o el usuario da "Atrás"
                    .setReturnUrl(frontendUrl + "/profile/settings/payments/success") // Cuando termina exitosamente
                    .setType(AccountLinkCreateParams.Type.ACCOUNT_ONBOARDING)
                    .build();

            AccountLink link = AccountLink.create(params);
            return link.getUrl();

        } catch (StripeException e) {
            log.error("❌ Error generando onboarding: {}", e.getMessage());
            throw new RuntimeException("Error al conectar cuenta bancaria", e);
        }
    }

    private MerchantAccount createMerchantAccount(Long userId, String email) {
        log.info("🆕 Registrando nueva cuenta Merchant Express para {}", email);
        try {
            // Configuración para México (Express)
            AccountCreateParams params = AccountCreateParams.builder()
                    .setType(AccountCreateParams.Type.EXPRESS)
                    .setCountry("MX") 
                    .setEmail(email)
                    .setCapabilities(AccountCreateParams.Capabilities.builder()
                        .setCardPayments(AccountCreateParams.Capabilities.CardPayments.builder().setRequested(true).build())
                        .setTransfers(AccountCreateParams.Capabilities.Transfers.builder().setRequested(true).build())
                        .build())
                    .setBusinessType(AccountCreateParams.BusinessType.INDIVIDUAL) // Por defecto Individual, luego ellos lo pueden cambiar en el UI de Stripe
                    .setMetadata(Map.of("userId", String.valueOf(userId)))
                    .build();

            Account account = Account.create(params);
            
            // Guardar en BD
            MerchantAccount newMerchant = MerchantAccount.builder()
                    .userId(userId)
                    .stripeAccountId(account.getId())
                    .country("MX")
                    .build();
            
            return merchantRepository.save(newMerchant);

        } catch (StripeException e) {
            log.error("❌ Error creando Merchant Account: {}", e.getMessage());
            throw new RuntimeException("Error al crear cuenta de vendedor", e);
        }
    }
}
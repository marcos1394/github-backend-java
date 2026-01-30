package com.quhealthy.payment_service.repository;

import com.quhealthy.payment_service.model.MerchantAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MerchantAccountRepository extends JpaRepository<MerchantAccount, Long> {
    Optional<MerchantAccount> findByUserId(Long userId);
    Optional<MerchantAccount> findByStripeAccountId(String stripeAccountId);
}
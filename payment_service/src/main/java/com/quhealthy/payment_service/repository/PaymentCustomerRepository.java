package com.quhealthy.payment_service.repository;

import com.quhealthy.payment_service.model.PaymentCustomer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentCustomerRepository extends JpaRepository<PaymentCustomer, Long> {
    Optional<PaymentCustomer> findByUserId(Long userId);
    Optional<PaymentCustomer> findByStripeCustomerId(String stripeCustomerId);
}
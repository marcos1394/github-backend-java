package com.quhealthy.payment_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PaymentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
        
        // Mensaje de confirmación en consola para saber que arrancó bien
        System.out.println("=========================================================");
        System.out.println("💳 Payment Service (Stripe/MP) iniciado en puerto 8084 🚀");
        System.out.println("=========================================================");
    }

}
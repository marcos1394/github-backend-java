package com.quhealthy.auth_service.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "plans")
public class Plan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String description;

    // --- Precios y Finanzas (BigDecimal es OBLIGATORIO para dinero) ---
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "transaction_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal transactionFee;

    @Column(name = "commission_rate", nullable = false, precision = 5, scale = 4) // Ej: 0.1500
    private BigDecimal commissionRate = new BigDecimal("0.15");

    @Column(name = "annual_discount", precision = 5, scale = 2)
    private BigDecimal annualDiscount;

    @Column(name = "default_advance_payment_percentage", precision = 5, scale = 2)
    private BigDecimal defaultAdvancePaymentPercentage;

    // --- Límites y Características ---
    @Column(name = "max_appointments")
    private Integer maxAppointments;

    @Column(name = "max_services")
    private Integer maxServices;

    @Column(name = "max_products")
    private Integer maxProducts;

    @Column(name = "max_courses")
    private Integer maxCourses;

    @Column(name = "marketing_level", nullable = false)
    private Integer marketingLevel;

    @Column(name = "support_level", nullable = false)
    private Integer supportLevel;

    @Column(name = "user_management", nullable = false)
    private Integer userManagement;

    // --- Flags Booleanos ---
    @Column(name = "qumarket_access", nullable = false)
    private boolean qumarketAccess;

    @Column(name = "qublocks_access", nullable = false)
    private boolean qublocksAccess;

    @Column(name = "advanced_reports", nullable = false)
    private boolean advancedReports;

    @Column(nullable = false)
    private boolean popular;

    @Column(name = "allow_advance_payments", nullable = false)
    private boolean allowAdvancePayments;

    // --- Configuración Externa ---
    @Column(nullable = false)
    private String duration; // "MONTHLY", "YEARLY" (Podría ser Enum, string está bien por ahora)

    @Column(name = "stripe_price_id")
    private String stripePriceId;

    // --- Timestamps ---
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
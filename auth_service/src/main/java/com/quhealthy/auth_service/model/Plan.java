package com.quhealthy.auth_service.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Modelo de Plan para el contexto de AUTENTICACIÓN Y PERMISOS.
 * Mapea a la misma tabla física 'plans' que usa payment_service.
 * * DIFERENCIA:
 * - Payment: Le importa CÓMO COBRAR (Stripe ID, MercadoPago ID).
 * - Auth: Le importa QUÉ PUEDES HACER (Límites, Accesos, Niveles).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "plans")
public class Plan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name; // "Plan Básico", "Plan Empresarial"

    @Column(length = 255)
    private String description;

    // Incluimos precio solo informativo para el perfil del usuario (no para cobrar)
    @Column(nullable = false)
    private BigDecimal price;

    // ========================================================================
    // 🚦 LÍMITES (Hard Limits para el "Cadenero")
    // ========================================================================

    @Column(name = "max_appointments")
    private Integer maxAppointments;

    @Column(name = "max_services")
    private Integer maxServices;

    @Column(name = "max_products")
    private Integer maxProducts;

    @Column(name = "max_courses")
    private Integer maxCourses;

    @Column(name = "user_management")
    private Integer userManagement; // Cuántos empleados puede tener

    // ========================================================================
    // 🔓 PERMISOS Y FEATURES (Boolean Flags)
    // ========================================================================

    @Column(name = "qumarket_access", nullable = false)
    private Boolean qumarketAccess; // ¿Aparece en el buscador global?

    @Column(name = "qublocks_access", nullable = false)
    private Boolean qublocksAccess; // ¿Puede usar el constructor de sitios web?

    @Column(name = "advanced_reports", nullable = false)
    private Boolean advancedReports;

    @Column(name = "allow_advance_payments", nullable = false)
    private Boolean allowAdvancePayments;

    // ========================================================================
    // 📊 NIVELES DE SERVICIO (Para lógica de UI/UX)
    // ========================================================================

    @Column(name = "marketing_level")
    private Integer marketingLevel; // 1=Básico, 4=Full Suite

    @Column(name = "support_level")
    private Integer supportLevel; // 1=Email, 4=Dedicado 24/7

    // ========================================================================
    // ⚙️ META
    // ========================================================================

    // Omitimos stripe_price_id y mp_plan_id aquí porque Auth no cobra.
    // Eso mantiene el modelo limpio y enfocado en permisos.

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
package com.quhealthy.payment_service.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;

/**
 * Modelo simplificado de Plan para el contexto de Pagos.
 * Solo mapeamos los campos necesarios para cobrar.
 * La lógica de permisos/features se queda en auth_service.
 */
@Data
@Entity
@Table(name = "plans") // Mapea a la misma tabla física que usa auth_service
public class Plan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "stripe_price_id")
    private String stripePriceId;

    // 👇 ESTE CAMPO ES EL NUEVO Y CRÍTICO PARA MERCADOPAGO
    // Asegúrate de haber corrido el script SQL para agregar esta columna en la BD
    @Column(name = "mp_plan_id")
    private String mpPlanId;
}
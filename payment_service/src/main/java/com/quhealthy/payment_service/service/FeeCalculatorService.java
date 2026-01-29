package com.quhealthy.payment_service.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class FeeCalculatorService {

    /**
     * 📊 Configuración de Comisiones por Plan (Basado en tu JSON)
     */
    @Getter
    @RequiredArgsConstructor
    public enum PlanConfig {
        // ID, Nombre, Comisión % (commission_rate), Costo Fijo por Transacción (transaction_fee)
        
        // ID 5: 0% fijo, 15% variable
        PLAN_GRATUITO(5L, "Plan Gratuito", new BigDecimal("0.1500"), BigDecimal.ZERO),
        
        // ID 1: $10.00 fijos, 15% variable
        PLAN_BASICO(1L, "Plan Básico", new BigDecimal("0.1500"), new BigDecimal("10.00")),
        
        // ID 2: $8.00 fijos, 12% variable
        PLAN_ESTANDAR(2L, "Plan Estándar", new BigDecimal("0.1200"), new BigDecimal("8.00")),
        
        // ID 3: $5.00 fijos, 10% variable
        PLAN_PREMIUM(3L, "Plan Premium", new BigDecimal("0.1000"), new BigDecimal("5.00")),
        
        // ID 4: $0.00 fijos, 5% variable
        PLAN_EMPRESARIAL(4L, "Plan Empresarial", new BigDecimal("0.0500"), BigDecimal.ZERO);

        private final Long dbId;
        private final String name;
        private final BigDecimal commissionPercentage; // commission_rate
        private final BigDecimal fixedTransactionFee; // transaction_fee

        // Mapa para buscar rápido por ID
        private static final Map<Long, PlanConfig> BY_ID = Arrays.stream(values())
                .collect(Collectors.toMap(PlanConfig::getDbId, Function.identity()));

        public static PlanConfig fromId(Long id) {
            return BY_ID.getOrDefault(id, PLAN_GRATUITO);
        }
    }

    /**
     * Calcula cuánto cobra la plataforma por una cita específica.
     * * @param appointmentPrice El precio que el doctor le cobra al paciente (ej: $1000).
     * @param planId El ID del plan que tiene el doctor actualmente.
     * @return El monto total que se queda la plataforma.
     */
    public BigDecimal calculatePlatformFee(BigDecimal appointmentPrice, Long planId) {
        // Validación de seguridad
        if (appointmentPrice == null || appointmentPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        // 1. Obtener las reglas del plan
        PlanConfig plan = PlanConfig.fromId(planId);

        // 2. Calcular el porcentaje sobre el PRECIO DE LA CITA
        // Ej: $1000 * 0.15 = $150
        BigDecimal percentageFee = appointmentPrice.multiply(plan.getCommissionPercentage());

        // 3. Sumar la tarifa fija por transacción (Si aplica según el plan)
        // Ej: $150 + $10 = $160
        BigDecimal totalFee = percentageFee.add(plan.getFixedTransactionFee());

        // 4. Redondeo financiero estándar (2 decimales)
        totalFee = totalFee.setScale(2, RoundingMode.HALF_UP);

        // Seguridad: Nunca cobrar más que el total de la cita
        if (totalFee.compareTo(appointmentPrice) > 0) {
            totalFee = appointmentPrice;
        }

        log.info("💰 Comisión Cita [Plan {}]: Cita ${} * {}% + ${} Fijos = Comisión ${}", 
                 plan.getName(), 
                 appointmentPrice, 
                 plan.getCommissionPercentage().multiply(BigDecimal.valueOf(100)), 
                 plan.getFixedTransactionFee(), 
                 totalFee);
        
        return totalFee;
    }
}
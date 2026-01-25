package com.quhealthy.payment_service.dto;

import com.quhealthy.payment_service.model.Subscription;
import org.springframework.stereotype.Component;

@Component
public class SubscriptionMapper {

    public SubscriptionResponse toResponse(Subscription entity) {
        if (entity == null) return null;

        return SubscriptionResponse.builder()
                .id(entity.getId())
                .planId(entity.getPlanId())
                .status(entity.getStatus())
                .gateway(entity.getGateway())
                .currentPeriodStart(entity.getCurrentPeriodStart())
                .currentPeriodEnd(entity.getCurrentPeriodEnd())
                // Usamos la lógica de negocio de la entidad para determinar si es válido visualmente
                .isActive(entity.isValid()) 
                // Si hay fecha de cancelación futura, es que se cancelará al final del periodo
                .cancelAtPeriodEnd(entity.getCanceledAt() != null)
                .build();
    }
}
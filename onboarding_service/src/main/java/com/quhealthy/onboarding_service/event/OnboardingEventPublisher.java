package com.quhealthy.onboarding_service.event;

import java.util.Map;

public interface OnboardingEventPublisher {

    /**
     * Publica un evento indicando que un paso del onboarding ha sido completado.
     * * @param userId ID del proveedor.
     * @param email Email del usuario (puede ser null si no se tiene a mano, el consumidor lo buscará).
     * @param stepName Nombre del paso (ej: "PROFILE", "KYC", "LICENSE").
     * @param extraData Map con datos adicionales para la notificación (ej: nombre del negocio).
     */
    void publishStepCompleted(Long userId, String email, String stepName, Map<String, Object> extraData);

    void publishStepRejected(Long userId, String email, String stepName, String reason);
}
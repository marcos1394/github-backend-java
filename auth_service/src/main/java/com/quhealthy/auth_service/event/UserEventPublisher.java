package com.quhealthy.auth_service.event;

/**
 * Puerto de salida (Port) para eventos de dominio.
 * Desacopla la lógica de negocio de la infraestructura de mensajería (GCP/Kafka/etc).
 */
public interface UserEventPublisher {
    void publish(UserEvent event);
}
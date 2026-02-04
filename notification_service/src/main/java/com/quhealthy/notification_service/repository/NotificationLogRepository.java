package com.quhealthy.notification_service.repository;

import com.quhealthy.notification_service.model.NotificationLog;
import com.quhealthy.notification_service.model.enums.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {

    /**
     * ✅ WEBHOOKS: Buscar log por el ID del proveedor (Twilio/Resend).
     * * Cuando Resend nos avisa "El email ID 123-abc rebotó", usamos este método
     * para encontrar nuestro registro y actualizar el estado a BOUNCED.
     */
    Optional<NotificationLog> findByProviderId(String providerId);

    /**
     * ✅ RETRY JOB: Buscar mensajes que se quedaron "pegados" o fallaron.
     * * Útil para un Cron Job que busque mensajes en estado FAILED o PENDING
     * creados hace X tiempo para intentar reenviarlos.
     */
    List<NotificationLog> findByStatusAndCreatedAtBefore(NotificationStatus status, LocalDateTime dateTime);

    /**
     * ✅ SOPORTE TÉCNICO: Ver historial de envíos a un usuario.
     * * Si un usuario se queja "No me llegan los correos", soporte puede buscar
     * aquí y ver si están saliendo o si están rebotando.
     */
    Page<NotificationLog> findByUserId(Long userId, Pageable pageable);
}
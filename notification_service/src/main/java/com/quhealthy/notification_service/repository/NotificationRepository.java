package com.quhealthy.notification_service.repository;

import com.quhealthy.notification_service.model.Notification;
import com.quhealthy.notification_service.model.enums.TargetRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * Obtiene las notificaciones paginadas de un usuario específico.
     * Ordenadas por fecha descendente (lo más nuevo arriba) gracias al Pageable.
     */
    Page<Notification> findByUserIdAndTargetRole(Long userId, TargetRole targetRole, Pageable pageable);

    /**
     * Cuenta cuántas notificaciones NO LEÍDAS tiene el usuario.
     * Vital para el numerito rojo (Badge) en la UI.
     */
    long countByUserIdAndTargetRoleAndIsReadFalse(Long userId, TargetRole targetRole);

    /**
     * Marcar TODAS como leídas de un golpe (Bulk Update).
     * Mucho más eficiente que traerlas y guardarlas una por una.
     */
    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.userId = :userId AND n.targetRole = :role AND n.isRead = false")
    void markAllAsRead(@Param("userId") Long userId, @Param("role") TargetRole role);
}
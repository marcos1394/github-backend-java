package com.quhealthy.auth_service.repository;

import com.quhealthy.auth_service.model.Notification;
import com.quhealthy.auth_service.model.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // Obtener notificaciones de un usuario ordenadas por fecha (las más nuevas primero)
    List<Notification> findByUserIdAndUserRoleOrderByCreatedAtDesc(Long userId, Role userRole);

    // Obtener solo las no leídas (para el contador de la campanita 🔔)
    long countByUserIdAndUserRoleAndIsReadFalse(Long userId, Role userRole);

    // Marcar todas como leídas (Query optimizada)
    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.userId = :userId AND n.userRole = :userRole")
    void markAllAsRead(@Param("userId") Long userId, @Param("userRole") Role userRole);
}
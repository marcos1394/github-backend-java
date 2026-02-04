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
     * ✅ UI HISTORIAL: Obtiene las notificaciones paginadas de un usuario.
     * * Filtra por el ROL para no mezclar notificaciones de Médico con las de Paciente.
     * El objeto Pageable se encarga del LIMIT, OFFSET y ORDER BY created_at DESC.
     */
    Page<Notification> findByUserIdAndTargetRole(Long userId, TargetRole targetRole, Pageable pageable);

    /**
     * ✅ UI BADGE: Cuenta cuántas notificaciones NO LEÍDAS tiene el usuario.
     * * Se usa para mostrar el numerito rojo en la campana de la App/Web.
     * Ejemplo: "Tienes (5) notificaciones nuevas".
     */
    long countByUserIdAndTargetRoleAndIsReadFalse(Long userId, TargetRole targetRole);

    /**
     * ✅ UI ACCIÓN: Marcar TODAS como leídas de un golpe.
     * * Usamos @Modifying y @Query para hacerlo eficiente a nivel de Base de Datos
     * en lugar de traer 100 objetos a memoria y guardarlos uno por uno.
     */
    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.userId = :userId AND n.targetRole = :role AND n.isRead = false")
    void markAllAsRead(@Param("userId") Long userId, @Param("role") TargetRole role);
}
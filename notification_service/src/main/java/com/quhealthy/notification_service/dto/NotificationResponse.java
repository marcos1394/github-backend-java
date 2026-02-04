package com.quhealthy.notification_service.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.quhealthy.notification_service.model.enums.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponse {

    private Long id;

    private String title;

    private String message;

    // INFO, SUCCESS, WARNING, ERROR, REMINDER, GEO_ALERT
    private NotificationType type;

    private boolean isRead;

    private String actionLink;

    /**
     * ✅ NUEVO: Datos extra para lógica avanzada en Frontend.
     * Ejemplo Geo: "{ 'lat': 19.43, 'lng': -99.13, 'storeId': 50 }"
     * El frontend hará JSON.parse(metadata) para usarlo.
     */
    private String metadata;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;
}
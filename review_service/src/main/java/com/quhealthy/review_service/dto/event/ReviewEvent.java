package com.quhealthy.review_service.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Este objeto viajará por la red a través de Google Pub/Sub.
 * Debe ser ligero y Serializable.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewEvent implements Serializable {
    private String eventType; // Ej: "REVIEW_CREATED", "PROVIDER_REPLIED"
    private Long reviewId;
    private Long consumerId;
    private Long providerId;
    private Integer rating;
    private String messageSnippet; // Un fragmento del comentario para el preview del email
}
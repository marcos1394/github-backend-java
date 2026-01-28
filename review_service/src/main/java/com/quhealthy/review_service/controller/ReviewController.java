package com.quhealthy.review_service.controller;

import com.quhealthy.review_service.dto.CreateReviewRequest;
import com.quhealthy.review_service.dto.ReplyReviewRequest;
import com.quhealthy.review_service.model.Review;
import com.quhealthy.review_service.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    /**
     * ✅ CREAR UNA RESEÑA (Solo Pacientes)
     * Endpoint: POST /api/reviews
     * Seguridad: Requiere Token JWT. El ID del consumidor se extrae del token.
     */
    @PostMapping
    public ResponseEntity<Review> createReview(
            @AuthenticationPrincipal Long consumerId, // 👈 Extraído del JWT automáticamente
            @Valid @RequestBody CreateReviewRequest request) {
        
        log.info("📥 Request Crear Reseña recibida del usuario: {}", consumerId);
        Review createdReview = reviewService.createReview(consumerId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdReview);
    }

    /**
     * ✅ OBTENER RESEÑAS DE UN DOCTOR (Público)
     * Endpoint: GET /api/reviews/provider/{providerId}
     * Parametros: ?page=0&size=10&sort=createdAt,desc (Opcionales, tiene defaults)
     */
    @GetMapping("/provider/{providerId}")
    public ResponseEntity<Page<Review>> getProviderReviews(
            @PathVariable Long providerId,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        
        return ResponseEntity.ok(reviewService.getReviewsForProvider(providerId, pageable));
    }

    /**
     * ✅ OBTENER ESTADÍSTICAS (Público)
     * Endpoint: GET /api/reviews/provider/{providerId}/stats
     * Retorna: { "averageRating": 4.8, "totalReviews": 150 }
     */
    @GetMapping("/provider/{providerId}/stats")
    public ResponseEntity<Map<String, Object>> getProviderStats(@PathVariable Long providerId) {
        return ResponseEntity.ok(reviewService.getProviderStats(providerId));
    }

    /**
     * ✅ RESPONDER A UNA RESEÑA (Solo Doctores)
     * Endpoint: POST /api/reviews/{reviewId}/reply
     * Seguridad: Requiere Token JWT. El ID del doctor se extrae del token.
     */
    @PostMapping("/{reviewId}/reply")
    public ResponseEntity<Review> replyToReview(
            @AuthenticationPrincipal Long providerId, // 👈 ID del doctor autenticado
            @PathVariable Long reviewId,
            @Valid @RequestBody ReplyReviewRequest request) {
        
        log.info("📥 Request Respuesta recibida del provider: {} para review: {}", providerId, reviewId);
        Review updatedReview = reviewService.replyToReview(providerId, reviewId, request);
        return ResponseEntity.ok(updatedReview);
    }
}
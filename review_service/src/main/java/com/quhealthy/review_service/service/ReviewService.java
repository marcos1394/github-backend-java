package com.quhealthy.review_service.service;

import com.quhealthy.review_service.dto.CreateReviewRequest;
import com.quhealthy.review_service.dto.ReplyReviewRequest;
import com.quhealthy.review_service.dto.event.ReviewEvent;
import com.quhealthy.review_service.event.ReviewEventPublisher;
import com.quhealthy.review_service.model.Review;
import com.quhealthy.review_service.repository.ReviewRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ReviewEventPublisher eventPublisher; // 👈 Nuestro conector con Google Pub/Sub

    /**
     * ✅ CREAR RESEÑA
     * Valida reglas de negocio, guarda en BD y emite evento asíncrono.
     */
    @Transactional
    public Review createReview(Long consumerId, CreateReviewRequest request) {
        log.info("📝 CreateReview: Consumer [{}] -> Provider [{}]", consumerId, request.getProviderId());

        // 1. Regla: No auto-reseñas
        if (consumerId.equals(request.getProviderId())) {
            throw new IllegalArgumentException("No puedes escribir una reseña sobre ti mismo.");
        }

        // 2. Regla: Un solo review por par Usuario-Doctor (Idempotencia)
        if (reviewRepository.existsByConsumerIdAndProviderId(consumerId, request.getProviderId())) {
            throw new IllegalArgumentException("Ya has calificado a este especialista anteriormente.");
        }

        // 3. Persistencia
        Review newReview = Review.builder()
                .consumerId(consumerId)
                .providerId(request.getProviderId())
                .serviceId(request.getServiceId())
                .rating(request.getRating())
                .comment(request.getComment())
                .isVerified(false) // TODO: Integrar con Payment Service para marcar 'true' si hubo pago
                .build();

        Review savedReview = reviewRepository.save(newReview);

        // 4. Evento Asíncrono (Fire & Forget)
        // Construimos el DTO del evento para Google Pub/Sub
        ReviewEvent event = ReviewEvent.builder()
                .eventType("REVIEW_CREATED")
                .reviewId(savedReview.getId())
                .consumerId(consumerId)
                .providerId(request.getProviderId())
                .rating(request.getRating())
                .messageSnippet(truncate(request.getComment(), 50))
                .build();
        
        eventPublisher.publishEvent(event);

        return savedReview;
    }

    /**
     * ✅ OBTENER RESEÑAS (PAGINADO)
     * Optimizado con readOnly=true para alto tráfico.
     */
    @Transactional(readOnly = true)
    public Page<Review> getReviewsForProvider(Long providerId, Pageable pageable) {
        return reviewRepository.findByProviderId(providerId, pageable);
    }

    /**
     * ✅ OBTENER ESTADÍSTICAS
     * Devuelve el promedio y total para mostrar en la tarjeta del doctor.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getProviderStats(Long providerId) {
        Double average = reviewRepository.getAverageRatingByProvider(providerId);
        long total = reviewRepository.countByProviderId(providerId);

        Map<String, Object> stats = new HashMap<>();
        // Redondeo a 1 decimal (ej: 4.6)
        stats.put("averageRating", average != null ? Math.round(average * 10.0) / 10.0 : 0.0);
        stats.put("totalReviews", total);
        
        return stats;
    }

    /**
     * ✅ RESPONDER RESEÑA
     * Solo el doctor dueño de la reseña puede responder.
     */
    @Transactional
    public Review replyToReview(Long providerId, Long reviewId, ReplyReviewRequest request) {
        log.info("💬 ReplyReview: Provider [{}] -> Review [{}]", providerId, reviewId);

        // 1. Buscar
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new EntityNotFoundException("Reseña no encontrada"));

        // 2. Seguridad (Ownership)
        if (!review.getProviderId().equals(providerId)) {
            throw new SecurityException("No tienes permiso para responder a esta reseña.");
        }

        // 3. Regla: Solo una respuesta
        if (review.getProviderResponse() != null) {
            throw new IllegalArgumentException("Ya existe una respuesta para esta reseña.");
        }

        // 4. Actualizar
        review.setProviderResponse(request.getResponseText());
        review.setResponseAt(LocalDateTime.now());
        
        Review updatedReview = reviewRepository.save(review);

        // 5. Evento Asíncrono
        ReviewEvent event = ReviewEvent.builder()
                .eventType("PROVIDER_REPLIED")
                .reviewId(updatedReview.getId())
                .consumerId(review.getConsumerId()) // Avisar al paciente
                .providerId(providerId)
                .messageSnippet(truncate(request.getResponseText(), 50))
                .build();

        eventPublisher.publishEvent(event);

        return updatedReview;
    }

    // --- Helper ---
    private String truncate(String text, int length) {
        if (text == null) return "";
        return text.length() > length ? text.substring(0, length) + "..." : text;
    }
}
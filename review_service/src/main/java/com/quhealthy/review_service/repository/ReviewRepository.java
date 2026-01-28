package com.quhealthy.review_service.repository;

import com.quhealthy.review_service.model.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    // ✅ ESTE ES EL MÉTODO QUE TE FALTA Y CAUSA EL ERROR
    // Spring Data JPA genera la consulta automáticamente basándose en el nombre
    boolean existsByConsumerIdAndProviderId(Long consumerId, Long providerId);

    // ✅ Paginación para obtener reseñas (Usado en el Service Enterprise)
    Page<Review> findByProviderId(Long providerId, Pageable pageable);

    // ✅ Cálculo de Promedio (Usado para las stats)
    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.providerId = :providerId")
    Double getAverageRatingByProvider(@Param("providerId") Long providerId);

    // ✅ Conteo total
    long countByProviderId(Long providerId);

    // (Opcional) Método legado por si alguna parte vieja lo usa
    List<Review> findByProviderIdOrderByCreatedAtDesc(Long providerId);
}
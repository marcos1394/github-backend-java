package com.quhealthy.social_service.repository;

import com.quhealthy.social_service.model.ScheduledPost;
import com.quhealthy.social_service.model.enums.PostStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface ScheduledPostRepository extends JpaRepository<ScheduledPost, UUID> {

    // 1. QUERY CRÍTICO PARA EL CRON JOB
    // Trae posts que:
    // a) Están en estado SCHEDULED
    // b) Su hora de publicación (scheduledAt) ya pasó o es ahora (<= now)
    List<ScheduledPost> findByStatusAndScheduledAtLessThanEqual(PostStatus status, LocalDateTime now);

    // Ver posts de un doctor ordenados por fecha
    List<ScheduledPost> findByProviderIdOrderByScheduledAtDesc(Long providerId);
}
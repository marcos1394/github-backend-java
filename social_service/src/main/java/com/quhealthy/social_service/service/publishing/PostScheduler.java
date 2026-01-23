package com.quhealthy.social_service.service.publishing;

import com.quhealthy.social_service.model.ScheduledPost;
import com.quhealthy.social_service.model.enums.PostStatus;
import com.quhealthy.social_service.repository.ScheduledPostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PostScheduler {

    private final ScheduledPostRepository postRepository;
    private final SocialPublishingService publishingService;

    // Se ejecuta cada minuto (60000 ms)
    @Scheduled(fixedRate = 60000)
    public void processScheduledPosts() {
        log.debug("⏰ Verificando posts programados...");

        LocalDateTime now = LocalDateTime.now();
        // Buscar posts que sean SCHEDULED y cuya hora ya pasó (<= now)
        List<ScheduledPost> postsToPublish = postRepository.findByStatusAndScheduledAtLessThanEqual(
                PostStatus.SCHEDULED, now
        );

        if (postsToPublish.isEmpty()) return;

        log.info("📢 Encontrados {} posts para publicar.", postsToPublish.size());

        for (ScheduledPost post : postsToPublish) {
            try {
                // Publicar
                String platformPostId = publishingService.publish(post);
                
                // Actualizar BD (Éxito)
                post.setStatus(PostStatus.PUBLISHED);
                post.setPlatformPostId(platformPostId);
                post.setErrorMessage(null);
                log.info("✅ Post publicado exitosamente. ID: {}", platformPostId);

            } catch (Exception e) {
                // Actualizar BD (Fallo)
                post.setStatus(PostStatus.FAILED);
                post.setErrorMessage(e.getMessage());
                log.error("❌ Error publicando post {}: {}", post.getId(), e.getMessage());
            } finally {
                postRepository.save(post);
            }
        }
    }
}
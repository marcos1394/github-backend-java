package com.quhealthy.auth_service.repository;

import com.quhealthy.auth_service.model.ProviderCourse; // Asumo que existe el modelo
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProviderCourseRepository extends JpaRepository<ProviderCourse, Long> {
    long countByProviderId(Long providerId);
}
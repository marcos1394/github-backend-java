package com.quhealthy.onboarding_service.repository;

import com.quhealthy.onboarding_service.model.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TagRepository extends JpaRepository<Tag, Long> {
    // JpaRepository ya nos da findAll(), findById(), save(), etc.
}
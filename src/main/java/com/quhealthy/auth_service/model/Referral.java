package com.quhealthy.auth_service.model;

import com.quhealthy.auth_service.model.enums.ReferralStatus;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "referrals")
public class Referral {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // El que invitó (Referrer)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "referrer_id", nullable = false)
    private Provider referrer;

    // El invitado (Referee) - Usualmente es 1 a 1 en el contexto de registro
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "referee_id", nullable = false, unique = true)
    private Provider referee;

    // Estado (Usamos el Enum nuevo)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReferralStatus status = ReferralStatus.PENDING;

    // --- Timestamps ---
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
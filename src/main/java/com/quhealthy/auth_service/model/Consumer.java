package com.quhealthy.auth_service.model;

import com.quhealthy.auth_service.model.enums.Role;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true) // Incluye los campos del padre en el equals/hash
@Entity
@Table(name = "consumers")
public class Consumer extends BaseUser {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.CONSUMER;

    @Column(name = "profile_image_url", columnDefinition = "TEXT")
    private String profileImageUrl;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "preferred_language")
    private String preferredLanguage = "es";

    // --- Notificaciones ---
    @Column(name = "email_notifications_enabled")
    private boolean emailNotificationsEnabled = true;

    @Column(name = "sms_notifications_enabled")
    private boolean smsNotificationsEnabled = false;

    @Column(name = "marketing_emails_opt_in")
    private boolean marketingEmailsOptIn = false;

    // Relaciones (JPA)
    // @OneToMany(mappedBy = "consumer")
    // private List<Review> reviews; 
    // Nota: Descomentar cuando crees la entidad Review
}
package com.quhealthy.social_service.model.enums;

public enum PostStatus {
    DRAFT,      // Borrador (Generado por IA pero no aprobado)
    SCHEDULED,  // Aprobado y esperando hora de publicación
    PUBLISHED,  // Ya está en la red social
    FAILED      // Hubo un error al publicar
}
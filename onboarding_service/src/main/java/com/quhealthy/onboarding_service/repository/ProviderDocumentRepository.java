package com.quhealthy.onboarding_service.repository;

import com.quhealthy.onboarding_service.model.ProviderDocument;
import com.quhealthy.onboarding_service.model.enums.DocumentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProviderDocumentRepository extends JpaRepository<ProviderDocument, Long> {

    /**
     * Busca un documento específico de un proveedor (ej: La INE Frontal de Juan).
     * Útil para sobreescribirlo si lo sube de nuevo o consultar su estado.
     */
    Optional<ProviderDocument> findByProviderIdAndDocumentType(Long providerId, DocumentType documentType);

    /**
     * Trae todos los documentos que ha subido un proveedor.
     * Útil para validar si ya completó el set (Frente + Reverso).
     */
    List<ProviderDocument> findAllByProviderId(Long providerId);
}
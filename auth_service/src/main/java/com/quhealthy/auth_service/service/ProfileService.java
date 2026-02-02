package com.quhealthy.auth_service.service;

import com.quhealthy.auth_service.dto.request.UpdateConsumerProfileRequest;
import com.quhealthy.auth_service.dto.request.UpdateProviderProfileRequest;
import com.quhealthy.auth_service.dto.response.MessageResponse;
import com.quhealthy.auth_service.model.CategoryProvider;
import com.quhealthy.auth_service.model.Consumer;
import com.quhealthy.auth_service.model.Provider;
import com.quhealthy.auth_service.model.SubCategory;
import com.quhealthy.auth_service.model.Tag;
import com.quhealthy.auth_service.model.enums.Gender;
import com.quhealthy.auth_service.repository.CategoryProviderRepository;
import com.quhealthy.auth_service.repository.ConsumerRepository;
import com.quhealthy.auth_service.repository.ProviderRepository;
import com.quhealthy.auth_service.repository.SubCategoryRepository;
import com.quhealthy.auth_service.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileService {

    private final ConsumerRepository consumerRepository;
    private final ProviderRepository providerRepository;

    // Repositorios auxiliares para relaciones del Provider
    private final CategoryProviderRepository categoryRepository;
    private final SubCategoryRepository subCategoryRepository;
    private final TagRepository tagRepository;

    // Factoría para crear puntos geográficos (SRID 4326 = WGS84 Estándar GPS)

    // ========================================================================
    // 👤 CONSUMER PROFILE (PACIENTES)
    // ========================================================================

    /**
     * Obtiene el perfil completo del Consumidor actual.
     * (Método para endpoint GET /api/profile/consumer/me)
     */
    @Transactional(readOnly = true)
    public Consumer getConsumerProfile(Long consumerId) {
        return consumerRepository.findById(consumerId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
    }

    /**
     * Actualiza parcialmente el perfil del Consumidor.
     * Solo actualiza campos que no sean NULL en el request.
     */
    @Transactional
    public MessageResponse updateConsumerProfile(Long consumerId, UpdateConsumerProfileRequest request) {
        Consumer consumer = consumerRepository.findById(consumerId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        log.info("Actualizando perfil de Consumer ID: {}", consumerId);

        // 1. Datos Básicos
        if (request.getFirstName() != null) consumer.setFirstName(request.getFirstName());
        if (request.getLastName() != null) consumer.setLastName(request.getLastName());
        if (request.getPhone() != null) {
            // Si cambia el teléfono, deberíamos invalidar la verificación (Regla de negocio)
            if (!request.getPhone().equals(consumer.getPhone())) {
                consumer.setPhoneVerified(false);
            }
            consumer.setPhone(request.getPhone());
        }
        if (request.getProfileImageUrl() != null) consumer.setProfileImageUrl(request.getProfileImageUrl());

        // 2. Datos Personales
        if (request.getBio() != null) consumer.setBio(request.getBio());
        if (request.getBirthDate() != null) consumer.setBirthDate(request.getBirthDate());
        if (request.getGender() != null) {
            try {
                consumer.setGender(Gender.valueOf(request.getGender()));
            } catch (IllegalArgumentException e) {
                // Si envían algo raro, lo ignoramos o lanzamos error (aquí ignoramos)
                log.warn("Género inválido recibido: {}", request.getGender());
            }
        }

        // 3. Preferencias
        if (request.getPreferredLanguage() != null) consumer.setPreferredLanguage(request.getPreferredLanguage());
        if (request.getTimezone() != null) consumer.setTimezone(request.getTimezone());

        // 4. Notificaciones
        if (request.getEmailNotificationsEnabled() != null) consumer.setEmailNotificationsEnabled(request.getEmailNotificationsEnabled());
        if (request.getSmsNotificationsEnabled() != null) consumer.setSmsNotificationsEnabled(request.getSmsNotificationsEnabled());
        if (request.getMarketingEmailsOptIn() != null) consumer.setMarketingEmailsOptIn(request.getMarketingEmailsOptIn());
        if (request.getAppointmentRemindersEnabled() != null) consumer.setAppointmentRemindersEnabled(request.getAppointmentRemindersEnabled());

        consumerRepository.save(consumer);
        return new MessageResponse("Perfil de paciente actualizado exitosamente.");
    }

    // ========================================================================
    // 🏢 PROVIDER PROFILE (DOCTORES / ESPECIALISTAS)
    // ========================================================================

    /**
     * Obtiene el perfil completo del Proveedor actual.
     * (Método para endpoint GET /api/profile/provider/me)
     */
    @Transactional(readOnly = true)
    public Provider getProviderProfile(Long providerId) {
        return providerRepository.findById(providerId)
                .orElseThrow(() -> new IllegalArgumentException("Proveedor no encontrado"));
    }

    /**
     * Actualiza parcialmente el perfil del Proveedor.
     * Maneja lógica compleja de Geolocalización y Relaciones (Categorías/Tags).
     */
    @Transactional
    public MessageResponse updateProviderProfile(Long providerId, UpdateProviderProfileRequest request) {
        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new IllegalArgumentException("Proveedor no encontrado"));

        log.info("Actualizando perfil de Provider ID: {}", providerId);

        // 1. Identidad de Negocio
        if (request.getBusinessName() != null) provider.setBusinessName(request.getBusinessName());
        if (request.getProfileImageUrl() != null) provider.setProfileImageUrl(request.getProfileImageUrl());
        if (request.getBio() != null) provider.setBio(request.getBio());

        // 2. Identidad Personal (Si aplica)
        if (request.getFirstName() != null) provider.setFirstName(request.getFirstName());
        if (request.getLastName() != null) provider.setLastName(request.getLastName());
        if (request.getGender() != null) {
            try {
                provider.setGender(Gender.valueOf(request.getGender()));
            } catch (IllegalArgumentException e) {
                log.warn("Género inválido recibido: {}", request.getGender());
            }
        }

        // 3. Contacto
        if (request.getPhone() != null) {
            // Regla de seguridad: Si cambia el teléfono principal, pierde verificación
            if (!request.getPhone().equals(provider.getPhone())) {
                provider.setPhoneVerified(false);
            }
            provider.setPhone(request.getPhone());
        }

        // 4. Ubicación y Geolocalización (PostGIS)
        if (request.getAddress() != null) provider.setAddress(request.getAddress());

        // Actualizamos coordenadas SOLO si vienen ambas (Lat y Lon)
        if (request.getLatitude() != null && request.getLongitude() != null) {
            provider.setLatitude(request.getLatitude());
            provider.setLongitude(request.getLongitude());

            // Crear Punto Geográfico para consultas espaciales
            // JTS Point usa (x, y) -> (longitude, latitude)

        }

        // 5. Categorización (Requiere buscar entidades)
        if (request.getCategoryProviderId() != null) {
            CategoryProvider category = categoryRepository.findById(request.getCategoryProviderId())
                    .orElseThrow(() -> new IllegalArgumentException("Categoría no válida"));
            provider.setCategory(category);
        }

        if (request.getSubCategoryId() != null) {
            SubCategory subCategory = subCategoryRepository.findById(request.getSubCategoryId())
                    .orElseThrow(() -> new IllegalArgumentException("Subcategoría no válida"));
            provider.setSubCategory(subCategory);
        }

        // 6. Etiquetas / Tags (Many-to-Many)
        if (request.getTagIds() != null) {
            // Buscamos todos los tags por sus IDs
            Set<Tag> newTags = new HashSet<>(tagRepository.findAllById(request.getTagIds()));

            // Reemplazamos la colección existente
            // JPA manejará la tabla intermedia 'provider_tags' automáticamente
            provider.setTags(newTags);
        }

        providerRepository.save(provider);
        return new MessageResponse("Perfil profesional actualizado exitosamente.");
    }
}
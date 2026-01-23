package com.quhealthy.onboarding_service.service;
import com.quhealthy.onboarding_service.dto.UpdateProfileRequest; // Asegúrate de importar el DTO
import com.quhealthy.onboarding_service.dto.TagDto;
import com.quhealthy.onboarding_service.model.Provider;
import com.quhealthy.onboarding_service.model.Tag;
import com.quhealthy.onboarding_service.repository.ProviderRepository;
import com.quhealthy.onboarding_service.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OnboardingService {

    private final TagRepository tagRepository;
    private final ProviderRepository providerRepository;

    // =================================================================
    // 1. CATÁLOGO GLOBAL (Para que el front muestre opciones)
    // =================================================================
    @Transactional(readOnly = true)
    public List<TagDto> getAllGlobalTags() {
        return tagRepository.findAll(Sort.by("name")).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    // =================================================================
    // 2. MIS ETIQUETAS (Para mostrar lo que ya seleccionó)
    // =================================================================
    @Transactional(readOnly = true)
    public List<TagDto> getProviderTags(Long providerId) {
        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new RuntimeException("Proveedor no encontrado. ID: " + providerId));

        return provider.getTags().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    // =================================================================
    // 3. ACTUALIZAR (Guardar selección)
    // =================================================================
    @Transactional
    public void updateProviderTags(Long providerId, List<Long> tagIds) {
        log.info("🔄 [Onboarding] Actualizando tags para Provider ID: {}", providerId);

        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new RuntimeException("Proveedor no encontrado. ID: " + providerId));

        // Validar y buscar los tags reales
        List<Tag> tags = tagRepository.findAllById(tagIds);
        
        // Reemplazar la colección actual con la nueva (Hibernate gestiona la tabla intermedia)
        provider.setTags(new HashSet<>(tags));
        
        providerRepository.save(provider);
        log.info("✅ Tags actualizados. Total asignados: {}", tags.size());
    }

    // =================================================================
    // 4. ELIMINAR TODO (Reset)
    // =================================================================
    @Transactional
    public void removeAllProviderTags(Long providerId) {
        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new RuntimeException("Proveedor no encontrado."));

        provider.getTags().clear();
        providerRepository.save(provider);
        log.info("🗑️ Todos los tags eliminados para Provider ID: {}", providerId);
    }

    /**
     * PASO 1 (Nivelación): Completa los datos básicos de negocio.
     * Esencial para usuarios de Google Login que no tienen teléfono ni nombre comercial.
     */
    @Transactional
    public Provider updateBusinessProfile(Long providerId, UpdateProfileRequest request) {
        log.info("📝 [Onboarding] Actualizando datos base para Provider ID: {}", providerId);

        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new RuntimeException("Proveedor no encontrado con ID: " + providerId));

        // 1. Actualizar Datos Críticos
        provider.setBusinessName(request.getBusinessName());
        provider.setPhone(request.getPhone());
        provider.setArchetype(request.getArchetype());
        provider.setParentCategoryId(request.getParentCategoryId());

        // 2. Subcategoría (si aplica)
        if (request.getSubCategoryId() != null) {
            provider.setSubCategoryId(request.getSubCategoryId());
        }

        // NOTA: No marcamos onboardingComplete=true ni pedimos dirección aquí.
        // Solo aseguramos que el usuario tenga identidad de negocio.

        return providerRepository.save(provider);
    }

    // Helper privado para convertir a DTO
    private TagDto mapToDto(Tag tag) {
        return new TagDto(tag.getId(), tag.getName(), tag.getColor());
    }
}
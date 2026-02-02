package com.quhealthy.auth_service.service;

import com.quhealthy.auth_service.dto.request.UpdateConsumerProfileRequest;
import com.quhealthy.auth_service.dto.request.UpdateProviderProfileRequest;
import com.quhealthy.auth_service.dto.response.MessageResponse;
import com.quhealthy.auth_service.model.*;
import com.quhealthy.auth_service.model.enums.Gender;
import com.quhealthy.auth_service.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    @Mock private ConsumerRepository consumerRepository;
    @Mock private ProviderRepository providerRepository;
    @Mock private CategoryProviderRepository categoryRepository;
    @Mock private SubCategoryRepository subCategoryRepository;
    @Mock private TagRepository tagRepository;

    @InjectMocks
    private ProfileService profileService;

    // ========================================================================
    // 👤 CONSUMER TESTS
    // ========================================================================

    @Test
    @DisplayName("getConsumerProfile: Debe retornar el consumidor si existe")
    void getConsumerProfile_ShouldReturnConsumer() {
        Long id = 1L;
        Consumer consumer = Consumer.builder().id(id).email("test@mail.com").build();
        when(consumerRepository.findById(id)).thenReturn(Optional.of(consumer));

        Consumer result = profileService.getConsumerProfile(id);

        assertNotNull(result);
        assertEquals("test@mail.com", result.getEmail());
    }

    @Test
    @DisplayName("updateConsumerProfile: Debe actualizar SOLO campos no nulos")
    void updateConsumerProfile_ShouldUpdatePartialFields() {
        Long id = 1L;
        Consumer existingUser = Consumer.builder()
                .id(id)
                .firstName("OldName")
                .lastName("OldLast")
                .phone("555-1111")
                .isPhoneVerified(true)
                .build();

        UpdateConsumerProfileRequest request = UpdateConsumerProfileRequest.builder()
                .firstName("NewName")
                .phone("555-9999")
                .gender("MALE")
                .birthDate(LocalDate.of(1990, 1, 1))
                .build();

        when(consumerRepository.findById(id)).thenReturn(Optional.of(existingUser));

        profileService.updateConsumerProfile(id, request);

        ArgumentCaptor<Consumer> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(consumerRepository).save(captor.capture());
        Consumer savedUser = captor.getValue();

        assertEquals("NewName", savedUser.getFirstName());
        assertEquals("OldLast", savedUser.getLastName());
        assertEquals("555-9999", savedUser.getPhone());
        assertFalse(savedUser.isPhoneVerified());
    }

    // ========================================================================
    // 🏢 PROVIDER TESTS
    // ========================================================================

    @Test
    @DisplayName("updateProviderProfile: Debe actualizar Geo y Relaciones")
    void updateProviderProfile_ShouldUpdateGeoAndRelations() {
        Long id = 2L;
        Provider existingProvider = Provider.builder().id(id).businessName("Old").build();

        UpdateProviderProfileRequest request = UpdateProviderProfileRequest.builder()
                .businessName("New")
                .latitude(19.4326).longitude(-99.1332)
                .categoryProviderId(10L).subCategoryId(20L)
                .tagIds(Set.of(5L, 6L))
                .build();

        CategoryProvider mockCategory = new CategoryProvider(); mockCategory.setId(10L);
        SubCategory mockSubCategory = new SubCategory(); mockSubCategory.setId(20L);
        Tag tag1 = new Tag(); tag1.setId(5L);
        Tag tag2 = new Tag(); tag2.setId(6L);

        when(providerRepository.findById(id)).thenReturn(Optional.of(existingProvider));
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(mockCategory));
        when(subCategoryRepository.findById(20L)).thenReturn(Optional.of(mockSubCategory));

        // CORRECCIÓN: Usamos any() para ser menos estrictos con la colección de IDs
        when(tagRepository.findAllById(any())).thenReturn(List.of(tag1, tag2));

        profileService.updateProviderProfile(id, request);

        ArgumentCaptor<Provider> captor = ArgumentCaptor.forClass(Provider.class);
        verify(providerRepository).save(captor.capture());
        Provider saved = captor.getValue();

        assertEquals("New", saved.getBusinessName());
        assertEquals(19.4326, saved.getLatitude());
        assertEquals(10L, saved.getCategory().getId());
        assertEquals(2, saved.getTags().size());
    }

    @Test
    @DisplayName("updateProviderProfile: Debe lanzar error si la categoría no existe")
    void updateProviderProfile_ShouldThrow_WhenCategoryNotFound() {
        Long id = 2L;
        Provider provider = Provider.builder().id(id).build();
        UpdateProviderProfileRequest request = UpdateProviderProfileRequest.builder().categoryProviderId(999L).build();

        when(providerRepository.findById(id)).thenReturn(Optional.of(provider));
        when(categoryRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> profileService.updateProviderProfile(id, request));
    }

    @Test
    @DisplayName("updateProviderProfile: Debe ignorar género inválido")
    void updateProviderProfile_ShouldIgnoreInvalidGender() {
        Long id = 2L;
        Provider provider = Provider.builder().id(id).build();
        UpdateProviderProfileRequest request = UpdateProviderProfileRequest.builder()
                .gender("ALIEN").businessName("Clinic").build();

        when(providerRepository.findById(id)).thenReturn(Optional.of(provider));

        profileService.updateProviderProfile(id, request);

        ArgumentCaptor<Provider> captor = ArgumentCaptor.forClass(Provider.class);
        verify(providerRepository).save(captor.capture());
        assertNull(captor.getValue().getGender());
    }
}
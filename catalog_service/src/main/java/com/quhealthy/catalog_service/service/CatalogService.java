package com.quhealthy.catalog_service.service;

import com.quhealthy.catalog_service.dto.request.CreatePackageRequest;
import com.quhealthy.catalog_service.dto.request.CreateServiceRequest;
import com.quhealthy.catalog_service.dto.response.PackageResponse;
import com.quhealthy.catalog_service.dto.response.ProviderCatalogResponse;
import com.quhealthy.catalog_service.dto.response.ServiceResponse;
import com.quhealthy.catalog_service.model.MedicalPackage;
import com.quhealthy.catalog_service.model.MedicalService;
import com.quhealthy.catalog_service.model.enums.ServiceStatus;
import com.quhealthy.catalog_service.repository.MedicalPackageRepository;
import com.quhealthy.catalog_service.repository.MedicalServiceRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CatalogService {

    private final MedicalServiceRepository serviceRepository;
    private final MedicalPackageRepository packageRepository;

    // TODO: Inyectar aquí el Feign Client del PaymentService en el futuro
    // private final PaymentServiceClient paymentClient;

    /**
     * ✅ CREAR SERVICIO INDIVIDUAL
     * Regla de Negocio: Validar límites del plan y duplicados.
     */
    @Transactional
    public ServiceResponse createService(Long providerId, CreateServiceRequest request) {
        log.info("🏥 Creando servicio para Provider ID: {}", providerId);

        // 1. Validar Límites del Plan (SaaS Logic)
        checkSubscriptionLimits(providerId);

        // 2. Evitar duplicados (Mismo nombre para el mismo doctor)
        if (serviceRepository.existsByProviderIdAndNameIgnoreCase(providerId, request.getName())) {
            throw new IllegalArgumentException("Ya tienes un servicio con el nombre: " + request.getName());
        }

        // 3. Crear Entidad
        MedicalService service = MedicalService.builder()
                .providerId(providerId)
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .currency(request.getCurrency())
                .durationMinutes(request.getDurationMinutes())
                .status(ServiceStatus.ACTIVE)
                .build();

        MedicalService saved = serviceRepository.save(service);
        return mapToServiceResponse(saved);
    }

    /**
     * ✅ CREAR PAQUETE (COMBO)
     * Regla de Negocio: Un paquete se compone de servicios existentes del MISMO doctor.
     */
    @Transactional
    public PackageResponse createPackage(Long providerId, CreatePackageRequest request) {
        log.info("🎁 Creando paquete para Provider ID: {}", providerId);

        checkSubscriptionLimits(providerId);

        // 1. Buscar los servicios solicitados y validar propiedad
        List<MedicalService> services = serviceRepository.findAllByIdInAndProviderId(request.getServiceIds(), providerId);

        // Si encontramos menos servicios de los que pidió, significa que envió IDs falsos o de otro doctor
        if (services.size() != request.getServiceIds().size()) {
            throw new IllegalArgumentException("Uno o más servicios no existen o no te pertenecen.");
        }

        // 2. Crear Entidad Paquete
        MedicalPackage medicalPackage = MedicalPackage.builder()
                .providerId(providerId)
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice()) // Precio con descuento definido por el doctor
                .currency(request.getCurrency())
                .status(ServiceStatus.ACTIVE)
                .services(new HashSet<>(services))
                .build();

        MedicalPackage saved = packageRepository.save(medicalPackage);
        return mapToPackageResponse(saved);
    }

    /**
     * ✅ OBTENER CATÁLOGO PÚBLICO (Para el Perfil del Doctor)
     * Regla de Negocio: Solo mostrar lo ACTIVE y calcular ahorros.
     */
    @Transactional(readOnly = true)
    public ProviderCatalogResponse getPublicCatalog(Long providerId) {
        // 1. Obtener Servicios Sueltos
        List<ServiceResponse> services = serviceRepository.findByProviderIdAndStatus(providerId, ServiceStatus.ACTIVE)
                .stream()
                .map(this::mapToServiceResponse)
                .toList();

        // 2. Obtener Paquetes
        List<PackageResponse> packages = packageRepository.findByProviderIdAndStatus(providerId, ServiceStatus.ACTIVE)
                .stream()
                .map(this::mapToPackageResponse) // Aquí adentro se calcula el ahorro
                .toList();

        return ProviderCatalogResponse.builder()
                .providerId(providerId)
                .individualServices(services)
                .packages(packages)
                .build();
    }

    /**
     * ✅ GESTIÓN INTERNA (Dashboard del Doctor)
     * Ve todo (Activo, Inactivo, Archivado).
     */
    @Transactional(readOnly = true)
    public Page<ServiceResponse> getMyServices(Long providerId, Pageable pageable) {
        return serviceRepository.findByProviderId(providerId, pageable)
                .map(this::mapToServiceResponse);
    }

    // =================================================================
    // 🛡️ REGLAS DE NEGOCIO & UTILIDADES
    // =================================================================

    /**
     * Placeholder para validación contra PaymentService.
     * En el futuro, esto hará una llamada síncrona (Feign) o consultará Redis.
     */
    private void checkSubscriptionLimits(Long providerId) {
        // TODO: Implementar lógica real.
        // Ejemplo:
        // SubscriptionPlan plan = paymentClient.getPlan(providerId);
        // long currentServices = serviceRepository.countByProviderId(providerId);
        // if (currentServices >= plan.getMaxServices()) {
        //     throw new IllegalStateException("Has alcanzado el límite de servicios de tu plan actual. Actualiza a Pro.");
        // }
        log.debug("✅ (Mock) Verificando límites de plan para provider {}", providerId);
    }

    // --- Mappers Manuales (Más rápido y seguro que ModelMapper) ---

    private ServiceResponse mapToServiceResponse(MedicalService s) {
        return ServiceResponse.builder()
                .id(s.getId())
                .name(s.getName())
                .description(s.getDescription())
                .price(s.getPrice())
                .currency(s.getCurrency())
                .durationMinutes(s.getDurationMinutes())
                .status(s.getStatus())
                .build();
    }

    private PackageResponse mapToPackageResponse(MedicalPackage p) {
        // 1. Calcular valor real (Suma de precios individuales)
        BigDecimal realValue = p.getServices().stream()
                .map(MedicalService::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 2. Calcular ahorro (Real - Precio Paquete)
        // Si el paquete es más caro que la suma (raro), el ahorro es 0.
        BigDecimal savings = realValue.subtract(p.getPrice()).max(BigDecimal.ZERO);

        return PackageResponse.builder()
                .id(p.getId())
                .name(p.getName())
                .description(p.getDescription())
                .price(p.getPrice())
                .currency(p.getCurrency())
                .status(p.getStatus())
                .services(p.getServices().stream().map(this::mapToServiceResponse).toList())
                .realValue(realValue)
                .savings(savings) // 👈 Dato clave para el Frontend ("Ahorras $500")
                .build();
    }
}
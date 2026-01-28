package com.quhealthy.catalog_service.dto.response;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class ProviderCatalogResponse {
    private Long providerId;
    // Lista de servicios sueltos
    private List<ServiceResponse> individualServices;
    // Lista de paquetes/combos (donde está el ahorro)
    private List<PackageResponse> packages;
}
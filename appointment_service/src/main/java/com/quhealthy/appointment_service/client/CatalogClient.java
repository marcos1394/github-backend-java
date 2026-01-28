package com.quhealthy.appointment_service.client;

import com.quhealthy.appointment_service.dto.response.CatalogServiceDto; // Lo definimos abajo
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

// Se conecta a la URL definida en application.properties
@FeignClient(name = "catalog-service", url = "${application.clients.catalog-service.url}")
public interface CatalogClient {

    @GetMapping("/api/catalog/services/{id}")
    CatalogServiceDto getServiceById(@PathVariable("id") Long id);
}
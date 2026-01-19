package com.quhealthy.auth_service.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserContextResponse {
    
    private String role; // "PROVIDER" o "CONSUMER"
    
    // Si es Provider, este campo vendrá lleno (y consumerData nulo)
    private ProviderStatusResponse providerData;
    
    // Si es Consumer, este campo vendrá lleno (y providerData nulo)
    private ConsumerStatusResponse consumerData;
}
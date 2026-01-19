package com.quhealthy.onboarding_service.dto;

import lombok.Data;
import java.util.List;

@Data
public class UpdateTagsRequest {
    // Recibimos solo los IDs. Ejemplo: [1, 5, 8]
    private List<Long> tagIds;
}
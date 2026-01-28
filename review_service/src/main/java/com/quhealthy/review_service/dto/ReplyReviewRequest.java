package com.quhealthy.review_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ReplyReviewRequest {

    @NotBlank(message = "La respuesta no puede estar vacía")
    @Size(max = 2000, message = "La respuesta es demasiado larga")
    private String responseText;
}
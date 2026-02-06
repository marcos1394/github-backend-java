package com.quhealthy.onboarding_service.controller;

import com.quhealthy.onboarding_service.dto.response.KycDocumentResponse;
import com.quhealthy.onboarding_service.model.enums.DocumentType;
import com.quhealthy.onboarding_service.service.KycService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/onboarding/kyc")
@RequiredArgsConstructor
public class KycController {

    private final KycService kycService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<KycDocumentResponse> uploadDocument(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam("type") DocumentType documentType,
            @RequestPart("file") MultipartFile file) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        return ResponseEntity.ok(kycService.uploadAndVerifyDocument(userId, file, documentType));
    }
}
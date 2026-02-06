package com.quhealthy.onboarding_service.controller;

import com.quhealthy.onboarding_service.dto.response.LicenseResponse;
import com.quhealthy.onboarding_service.service.LicenseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/onboarding/license")
@RequiredArgsConstructor
public class LicenseController {

    private final LicenseService licenseService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<LicenseResponse> uploadLicense(
            @RequestHeader("X-User-Id") Long userId,
            @RequestPart("file") MultipartFile file) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        return ResponseEntity.ok(licenseService.uploadAndVerifyLicense(userId, file));
    }
}
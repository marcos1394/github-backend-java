package com.quhealthy.onboarding_service.controller;

import com.quhealthy.onboarding_service.service.integration.GooglePlacesService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/google/places")
@RequiredArgsConstructor
public class GooglePlacesController {

    private final GooglePlacesService googlePlacesService;

    @GetMapping(value = "/autocomplete", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> autocomplete(@RequestParam String input) {
        if (input == null || input.length() < 3) return ResponseEntity.badRequest().build();
        return ResponseEntity.ok(googlePlacesService.getPlacePredictions(input));
    }

    @GetMapping(value = "/details", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> details(@RequestParam String placeId) {
        return ResponseEntity.ok(googlePlacesService.getPlaceDetails(placeId));
    }

    @GetMapping(value = "/geocode", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> reverseGeocode(@RequestParam Double lat, @RequestParam Double lng) {
        return ResponseEntity.ok(googlePlacesService.getReverseGeocoding(lat, lng));
    }
}

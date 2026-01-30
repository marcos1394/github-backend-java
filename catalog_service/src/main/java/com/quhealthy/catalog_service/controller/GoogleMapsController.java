package com.quhealthy.catalog_service.controller;

import com.quhealthy.catalog_service.service.GoogleMapsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/catalog/google")
@RequiredArgsConstructor
public class GoogleMapsController {

    private final GoogleMapsService googleMapsService;

    @PostMapping("/validate-address")
    public ResponseEntity<Map<String, Object>> validate(@RequestBody Map<String, String> body) {
        String address = body.get("address");
        return ResponseEntity.ok(googleMapsService.validateAddress(address));
    }

    @GetMapping("/autocomplete")
    public ResponseEntity<List<Map<String, String>>> autocomplete(@RequestParam String input) {
        return ResponseEntity.ok(googleMapsService.getAutocompletePredictions(input));
    }

    @GetMapping("/place-details/{placeId}")
    public ResponseEntity<Map<String, Object>> details(@PathVariable String placeId) {
        return ResponseEntity.ok(googleMapsService.getPlaceDetails(placeId));
    }
    
    @GetMapping("/search")
    public ResponseEntity<List<Map<String, Object>>> search(@RequestParam String query) {
        return ResponseEntity.ok(googleMapsService.findPlaceFromText(query));
    }
}
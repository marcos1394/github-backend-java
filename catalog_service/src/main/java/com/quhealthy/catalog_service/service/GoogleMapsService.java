package com.quhealthy.catalog_service.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.quhealthy.catalog_service.dto.google.GoogleValidationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class GoogleMapsService {

    private final WebClient webClient;
    private final String apiKey;

    public GoogleMapsService(@Value("${google.maps.api-key}") String apiKey) {
        this.apiKey = apiKey;
        
        // Configuramos el cliente para NO seguir redirecciones automáticamente en el caso de las fotos
        // para poder capturar la URL real (Location header).
        HttpClient httpClient = HttpClient.create().followRedirect(true);
        
        this.webClient = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    // =================================================================
    // 1. VALIDACIÓN DE DIRECCIONES (Address Validation API)
    // =================================================================
    public Map<String, Object> validateAddress(String address) {
        log.info("🔹 Validando dirección: {}", address);

        Map<String, Object> requestBody = Map.of("address", Map.of(
                "regionCode", "MX",
                "addressLines", List.of(address)
        ));

        try {
            GoogleValidationResponse response = webClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host("addressvalidation.googleapis.com")
                            .path("/v1:validateAddress")
                            .queryParam("key", apiKey)
                            .build())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(GoogleValidationResponse.class)
                    .block(); // Bloqueamos para simplicidad, idealmente sería reactivo

            return processValidationLogic(response);

        } catch (Exception e) {
            log.error("❌ Error validando dirección: {}", e.getMessage());
            throw new RuntimeException("Error en servicio de validación Google");
        }
    }

    private Map<String, Object> processValidationLogic(GoogleValidationResponse response) {
        if (response == null || response.getResult() == null) {
            throw new RuntimeException("Respuesta vacía de Google");
        }

        List<GoogleValidationResponse.AddressComponent> components = 
                response.getResult().getAddress().getAddressComponents();

        // Tu lógica de negocio original
        List<String> requiredTypes = List.of("route", "street_number", "postal_code", "locality");
        
        boolean areAllConfirmed = requiredTypes.stream().allMatch(reqType -> 
            components.stream().anyMatch(c -> 
                c.getComponentType().equals(reqType) && 
                "CONFIRMED".equals(c.getConfirmationLevel())
            )
        );

        String message = areAllConfirmed 
                ? "La dirección ingresada es válida y fue confirmada." 
                : "La dirección no pudo ser confirmada con precisión. Por favor, revisa los datos.";

        return Map.of(
                "isValid", areAllConfirmed,
                "message", message,
                "details", response.getResult()
        );
    }

    // =================================================================
    // 2. BUSCADOR DE LUGARES (Places API New - Text Search)
    // =================================================================
    public List<Map<String, Object>> findPlaceFromText(String query) {
        log.info("🔹 Buscando lugar: {}", query);

        try {
            JsonNode response = webClient.post()
                    .uri("https://places.googleapis.com/v1/places:searchText")
                    .header("X-Goog-Api-Key", apiKey)
                    .header("X-Goog-FieldMask", "places.id,places.displayName,places.formattedAddress,places.location")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("textQuery", query, "languageCode", "es"))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            List<Map<String, Object>> results = new ArrayList<>();
            if (response != null && response.has("places")) {
                response.get("places").forEach(place -> {
                    results.add(Map.of(
                            "placeId", place.get("id").asText(),
                            "name", place.get("displayName").get("text").asText(),
                            "address", place.get("formattedAddress").asText(),
                            "location", place.get("location")
                    ));
                });
            }
            return results;

        } catch (Exception e) {
            log.error("❌ Error en Text Search: {}", e.getMessage());
            throw new RuntimeException("Error buscando lugares");
        }
    }

    // =================================================================
    // 3. DETALLES DEL LUGAR + FOTO (Places API New)
    // =================================================================
    public Map<String, Object> getPlaceDetails(String placeId) {
        log.info("🔹 Detalles Place ID: {}", placeId);

        String fieldMask = "id,displayName,formattedAddress,location,internationalPhoneNumber,websiteUri,rating,userRatingCount,regularOpeningHours,photos";

        try {
            JsonNode place = webClient.get()
                    .uri("https://places.googleapis.com/v1/places/" + placeId)
                    .header("X-Goog-Api-Key", apiKey)
                    .header("X-Goog-FieldMask", fieldMask)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            String photoUrl = null;
            
            // Lógica de Foto (Mejorada)
            if (place.has("photos") && place.get("photos").size() > 0) {
                String photoName = place.get("photos").get(0).get("name").asText();
                photoUrl = getPhotoRedirectUrl(photoName);
            }

            // Construcción manual del mapa de respuesta (o usar DTO)
            Map<String, Object> details = new HashMap<>();
            details.put("placeId", place.get("id").asText());
            details.put("name", place.path("displayName").path("text").asText());
            details.put("address", place.path("formattedAddress").asText());
            details.put("photoUrl", photoUrl);
            // ... agregar resto de campos según necesidad ...

            return details;

        } catch (Exception e) {
            log.error("❌ Error en Place Details: {}", e.getMessage());
            throw new RuntimeException("Error obteniendo detalles del lugar");
        }
    }

    /**
     * Obtiene la URL real de la imagen siguiendo la redirección de Google.
     * WebClient sigue redirects por defecto, así que pedimos la URL final.
     */
    private String getPhotoRedirectUrl(String resourceName) {
        String url = "https://places.googleapis.com/v1/" + resourceName + "/media?key=" + apiKey + "&maxHeightPx=400";
        try {
            // Hacemos un HEAD o GET ligero, WebClient en Spring Boot sigue el redirect 
            // y nos puede dar la URI final si configuramos el cliente.
            // Opción más simple: Devolver la URL de Google y que el frontend la resuelva (img src).
            // Pero para mantener tu lógica de backend:
            
            return webClient.get()
                    .uri(url)
                    .exchangeToMono(response -> {
                        if (response.statusCode().is3xxRedirection()) {
                            return reactor.core.publisher.Mono.just(response.headers().header("Location").get(0));
                        }
                        // Si Google nos da 200 OK directo (es la imagen binaria), devolvemos la URL original
                        // para que el navegador la cargue.
                        return reactor.core.publisher.Mono.just(url);
                    })
                    .block();
        } catch (Exception e) {
            log.warn("No se pudo obtener URL de foto: {}", e.getMessage());
            return null;
        }
    }

    // =================================================================
    // 4. AUTOCOMPLETE (Places API New)
    // =================================================================
    public List<Map<String, String>> getAutocompletePredictions(String input) {
        try {
            JsonNode response = webClient.post()
                    .uri("https://places.googleapis.com/v1/places:autocomplete")
                    .header("X-Goog-Api-Key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of(
                            "input", input,
                            "locationBias", Map.of(
                                    "circle", Map.of(
                                            "center", Map.of("latitude", 23.6345, "longitude", -102.5528),
                                            "radius", 50000.0
                                    )
                            ),
                            "includedRegionCodes", List.of("mx"),
                            "languageCode", "es"
                    ))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            List<Map<String, String>> suggestions = new ArrayList<>();
            if (response.has("suggestions")) {
                response.get("suggestions").forEach(s -> {
                    JsonNode prediction = s.get("placePrediction");
                    suggestions.add(Map.of(
                            "description", prediction.get("text").get("text").asText(),
                            "placeId", prediction.get("placeId").asText()
                    ));
                });
            }
            return suggestions;

        } catch (Exception e) {
            throw new RuntimeException("Error en Autocomplete");
        }
    }
}
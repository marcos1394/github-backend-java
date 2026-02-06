package com.quhealthy.onboarding_service.controller;

import com.quhealthy.onboarding_service.config.TestConfig;
import com.quhealthy.onboarding_service.service.integration.GooglePlacesService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GooglePlacesController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import(TestConfig.class)
class GooglePlacesControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GooglePlacesService googlePlacesService;

    // JSON dummy de respuesta
    private static final String MOCK_JSON_RESPONSE = "{\"predictions\": []}";

    // ========================================================================
    // 🔍 AUTOCOMPLETE
    // ========================================================================

    @Test
    @DisplayName("GET /autocomplete - Should return 200 and JSON when input is valid (>3 chars)")
    void autocomplete_ShouldReturnPredictions() throws Exception {
        // GIVEN
        String input = "Hospital";
        when(googlePlacesService.getPlacePredictions(input)).thenReturn(MOCK_JSON_RESPONSE);

        // WHEN & THEN
        mockMvc.perform(get("/api/google/places/autocomplete")
                        .param("input", input)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().json(MOCK_JSON_RESPONSE));

        verify(googlePlacesService).getPlacePredictions(input);
    }

    @Test
    @DisplayName("GET /autocomplete - Should return 400 Bad Request when input is too short")
    void autocomplete_ShouldReturn400_WhenInputIsShort() throws Exception {
        // GIVEN - Input de 2 letras (la validación pide >= 3)
        String shortInput = "Ho";

        // WHEN & THEN
        mockMvc.perform(get("/api/google/places/autocomplete")
                        .param("input", shortInput))
                .andExpect(status().isBadRequest());

        // El servicio NO debe llamarse si la validación falla antes
        verify(googlePlacesService, org.mockito.Mockito.never()).getPlacePredictions(anyString());
    }

    @Test
    @DisplayName("GET /autocomplete - Should return 400 Bad Request when input is missing")
    void autocomplete_ShouldReturn400_WhenInputIsMissing() throws Exception {
        // WHEN & THEN
        mockMvc.perform(get("/api/google/places/autocomplete"))
                .andExpect(status().isBadRequest());
    }

    // ========================================================================
    // 🏢 PLACE DETAILS
    // ========================================================================

    @Test
    @DisplayName("GET /details - Should return 200 and Details JSON")
    void details_ShouldReturnPlaceDetails() throws Exception {
        // GIVEN
        String placeId = "ChIJ...";
        when(googlePlacesService.getPlaceDetails(placeId)).thenReturn(MOCK_JSON_RESPONSE);

        // WHEN & THEN
        mockMvc.perform(get("/api/google/places/details")
                        .param("placeId", placeId))
                .andExpect(status().isOk())
                .andExpect(content().string(MOCK_JSON_RESPONSE));

        verify(googlePlacesService).getPlaceDetails(placeId);
    }

    // ========================================================================
    // 📍 REVERSE GEOCODING
    // ========================================================================

    @Test
    @DisplayName("GET /geocode - Should return 200 and Address JSON")
    void reverseGeocode_ShouldReturnAddress() throws Exception {
        // GIVEN
        Double lat = 19.4326;
        Double lng = -99.1332;
        when(googlePlacesService.getReverseGeocoding(lat, lng)).thenReturn(MOCK_JSON_RESPONSE);

        // WHEN & THEN
        mockMvc.perform(get("/api/google/places/geocode")
                        .param("lat", String.valueOf(lat))
                        .param("lng", String.valueOf(lng)))
                .andExpect(status().isOk())
                .andExpect(content().string(MOCK_JSON_RESPONSE));

        verify(googlePlacesService).getReverseGeocoding(lat, lng);
    }

    @Test
    @DisplayName("GET /geocode - Should return 400 if params are missing")
    void reverseGeocode_ShouldReturn400_WhenParamsMissing() throws Exception {
        // Faltan parámetros requeridos, Spring MVC lanza 400 automáticamente
        mockMvc.perform(get("/api/google/places/geocode")
                        .param("lat", "19.0")) // Falta longitud
                .andExpect(status().isBadRequest());
    }
}
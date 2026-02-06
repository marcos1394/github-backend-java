package com.quhealthy.onboarding_service.service.integration;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.google.maps.GeoApiContext;
import com.google.maps.GeocodingApi;
import com.google.maps.PlaceDetailsRequest; // 👈 IMPORTANTE: Nuevo Import
import com.google.maps.PlacesApi;
import com.google.maps.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class GooglePlacesService {

    private final GeoApiContext context;

    private final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(LocalTime.class, new LocalTimeAdapter())
            .create();

    /**
     * AUTOCOMPLETE
     */
    public String getPlacePredictions(String input) {
        try {
            // ✅ CORRECCIÓN 1: Manejo de tipos.
            // La librería no permite pasar (ESTABLISHMENT, GEOCODE) juntos.
            // Para que el médico encuentre su "Consultorio X" (negocio) O su "Calle Y" (dirección),
            // NO ponemos filtro de tipos. Así Google devuelve todo lo relevante.
            AutocompletePrediction[] predictions = PlacesApi.placeAutocomplete(context, input, null)
                    .components(ComponentFilter.country("mx"))
                    .await();

            return gson.toJson(predictions);

        } catch (Exception e) {
            log.error("❌ Error en Google Autocomplete: {}", e.getMessage());
            throw new RuntimeException("Error al buscar lugares en Google Maps.");
        }
    }

    /**
     * DETAILS
     */
    public String getPlaceDetails(String placeId) {
        try {
            // ✅ CORRECCIÓN 2: Uso correcto de FieldMask.
            // Los enums están en PlaceDetailsRequest.FieldMask, no en PlaceDetails.Field
            PlaceDetails details = PlacesApi.placeDetails(context, placeId)
                    .fields(
                            // 1. Datos Básicos
                            PlaceDetailsRequest.FieldMask.NAME,
                            PlaceDetailsRequest.FieldMask.PLACE_ID,
                            PlaceDetailsRequest.FieldMask.TYPES,

                            // 2. Ubicación
                            PlaceDetailsRequest.FieldMask.FORMATTED_ADDRESS,
                            PlaceDetailsRequest.FieldMask.ADDRESS_COMPONENT,
                            PlaceDetailsRequest.FieldMask.GEOMETRY,

                            // 3. Contacto
                            PlaceDetailsRequest.FieldMask.INTERNATIONAL_PHONE_NUMBER,
                            PlaceDetailsRequest.FieldMask.WEBSITE,

                            // 4. Reputación
                            PlaceDetailsRequest.FieldMask.RATING,
                            PlaceDetailsRequest.FieldMask.USER_RATINGS_TOTAL,
                            PlaceDetailsRequest.FieldMask.BUSINESS_STATUS,

                            // 5. Contenido Rico
                            PlaceDetailsRequest.FieldMask.OPENING_HOURS,
                            PlaceDetailsRequest.FieldMask.PHOTOS
                    )
                    .await();

            return gson.toJson(details);

        } catch (Exception e) {
            log.error("❌ Error en Google Place Details: {}", e.getMessage());
            throw new RuntimeException("Error al obtener los detalles del negocio.");
        }
    }

    /**
     * REVERSE GEOCODING
     */
    public String getReverseGeocoding(Double lat, Double lng) {
        try {
            LatLng location = new LatLng(lat, lng);

            GeocodingResult[] results = GeocodingApi.reverseGeocode(context, location)
                    .language("es")
                    .resultType(AddressType.STREET_ADDRESS, AddressType.PREMISE)
                    .await();

            if (results.length > 0) {
                return gson.toJson(results[0]);
            }
            return "{}";

        } catch (Exception e) {
            log.error("❌ Error en Google Reverse Geocoding: {}", e.getMessage());
            throw new RuntimeException("Error al geocodificar las coordenadas.");
        }
    }

    // --- Adaptador GSON (Igual que antes) ---
    private static class LocalTimeAdapter extends TypeAdapter<LocalTime> {
        @Override
        public void write(JsonWriter out, LocalTime value) throws IOException {
            if (value == null) out.nullValue();
            else out.value(value.toString());
        }

        @Override
        public LocalTime read(JsonReader in) throws IOException {
            String time = in.nextString();
            return time != null ? LocalTime.parse(time) : null;
        }
    }
}
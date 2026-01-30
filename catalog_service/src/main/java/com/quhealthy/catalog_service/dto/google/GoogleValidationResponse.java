package com.quhealthy.catalog_service.dto.google; // 👈 ESTO ES CRÍTICO

import lombok.Data;
import java.util.List;

@Data
public class GoogleValidationResponse {
    private Result result;

    @Data
    public static class Result {
        private Verdict verdict;
        private Address address;
        private Geocode geocode;
    }

    @Data
    public static class Verdict {
        private String inputGranularity;
        private String validationGranularity;
        private String geocodeGranularity;
    }

    @Data
    public static class Address {
        private String formattedAddress;
        private List<AddressComponent> addressComponents;
    }

    @Data
    public static class AddressComponent {
        private ComponentName componentName;
        private String componentType;
        private String confirmationLevel; 
    }

    @Data
    public static class ComponentName {
        private String text;
    }
    
    @Data
    public static class Geocode {
        private Location location;
    }
    
    @Data
    public static class Location {
        private Double latitude;
        private Double longitude;
    }
}
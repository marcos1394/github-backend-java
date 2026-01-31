package com.quhealthy.appointment_service.config;

import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.security.GeneralSecurityException;

@Configuration
public class GoogleConfig {

    /**
     * Provee el transporte HTTP seguro para todas las llamadas a Google.
     * Es mejor crear una sola instancia (Singleton) para toda la app.
     */
    @Bean
    public NetHttpTransport netHttpTransport() throws GeneralSecurityException, IOException {
        return com.google.api.client.googleapis.javanet.GoogleNetHttpTransport.newTrustedTransport();
    }

    /**
     * Provee la fábrica de JSON.
     * Usamos GSON que es la recomendada actualmente por Google (JacksonFactory está obsoleta en algunas versiones).
     */
    @Bean
    public JsonFactory jsonFactory() {
        return GsonFactory.getDefaultInstance();
    }
}
package com.quhealthy.appointment_service.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.services.calendar.CalendarScopes;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class GoogleCalendarService {

    private final NetHttpTransport httpTransport;
    private final JsonFactory jsonFactory;
    
    // Variables inyectadas desde application.properties (que vienen de Secret Manager)
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final String applicationName;

    // El flujo de autorización se inicializa una sola vez
    private GoogleAuthorizationCodeFlow flow;

    public GoogleCalendarService(
            NetHttpTransport httpTransport,
            JsonFactory jsonFactory,
            @Value("${google.calendar.client-id}") String clientId,
            @Value("${google.calendar.client-secret}") String clientSecret,
            @Value("${google.calendar.redirect-uri}") String redirectUri,
            @Value("${google.calendar.application-name:QuHealthy}") String applicationName) {
        
        this.httpTransport = httpTransport;
        this.jsonFactory = jsonFactory;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
        this.applicationName = applicationName;
        
        initFlow();
    }

    private void initFlow() {
        try {
            // 1. Construimos los secretos manualmente (evita leer archivos físicos en Cloud Run)
            GoogleClientSecrets.Details details = new GoogleClientSecrets.Details();
            details.setClientId(clientId);
            details.setClientSecret(clientSecret);
            
            GoogleClientSecrets secrets = new GoogleClientSecrets();
            secrets.setWeb(details);

            // 2. Definimos los permisos (Scopes)
            List<String> scopes = Collections.singletonList(CalendarScopes.CALENDAR);

            // 3. Creamos el flujo
            this.flow = new GoogleAuthorizationCodeFlow.Builder(
                    httpTransport, 
                    jsonFactory, 
                    secrets, 
                    scopes)
                    .setAccessType("offline") // Importante para obtener Refresh Token
                    .setApprovalPrompt("force") // Para forzar que nos den refresh token siempre
                    .build();
            
            log.info("✅ Google Authorization Flow inicializado correctamente.");
            
        } catch (Exception e) {
            log.error("❌ Error fatal inicializando Google Calendar Service: {}", e.getMessage());
            // No lanzamos excepción aquí para no tumbar todo el microservicio, 
            // pero las funciones de calendario fallarán si se invocan.
        }
    }

    /**
     * Genera la URL para que el doctor autorice el acceso.
     */
    public String getAuthorizationUrl(Long providerId) {
        if (flow == null) throw new IllegalStateException("El servicio de Google Calendar no se inicializó correctamente.");
        
        // Pasamos el providerId en el "state" para saber quién es cuando Google nos responda
        return flow.newAuthorizationUrl()
                .setRedirectUri(redirectUri)
                .setState(String.valueOf(providerId)) 
                .build();
    }

    /**
     * Intercambia el código de autorización por tokens reales.
     */
    public void exchangeCodeForTokens(String code, Long providerId) throws IOException {
        if (flow == null) throw new IllegalStateException("El servicio de Google Calendar no se inicializó correctamente.");

        GoogleTokenResponse response = flow.newTokenRequest(code)
                .setRedirectUri(redirectUri)
                .execute();

        String accessToken = response.getAccessToken();
        String refreshToken = response.getRefreshToken();
        
        log.info("Tokens obtenidos exitosamente para Provider ID: {}", providerId);
        
        // TODO: GUARDAR LOS TOKENS EN LA BASE DE DATOS DEL DOCTOR
        // example: doctorRepository.updateTokens(providerId, accessToken, refreshToken);
    }
}
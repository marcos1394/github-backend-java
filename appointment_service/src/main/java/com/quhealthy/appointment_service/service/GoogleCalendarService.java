package com.quhealthy.appointment_service.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.Events;
import com.quhealthy.appointment_service.model.CalendarIntegration;
import com.quhealthy.appointment_service.model.TimeBlock;
import com.quhealthy.appointment_service.repository.CalendarIntegrationRepository;
import com.quhealthy.appointment_service.repository.TimeBlockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleCalendarService {

    private final CalendarIntegrationRepository integrationRepository;
    private final TimeBlockRepository timeBlockRepository;

    @Value("${google.client.id}")
    private String clientId;

    @Value("${google.client.secret}")
    private String clientSecret;

    @Value("${application.api.url}")
    private String apiUrl;

    private static final String REDIRECT_URI_SUFFIX = "/api/appointments/google/callback";
    private static final List<String> SCOPES = List.of(
            "https://www.googleapis.com/auth/calendar.events", 
            "https://www.googleapis.com/auth/calendar.readonly"
    );
    private static final GsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final NetHttpTransport HTTP_TRANSPORT = new NetHttpTransport();

    /**
     * 1. Generar URL de Auth
     */
    public String generateAuthUrl(Long providerId) {
        String redirectUri = apiUrl + REDIRECT_URI_SUFFIX;

        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, clientId, clientSecret, SCOPES)
                .setAccessType("offline")
                .setApprovalPrompt("force")
                .build();

        return flow.newAuthorizationUrl()
                .setRedirectUri(redirectUri)
                .setState(String.valueOf(providerId))
                .build();
    }

    /**
     * 2. Manejar Callback y guardar tokens
     */
    public void handleCallback(String code, String state) throws IOException {
        Long providerId = Long.valueOf(state);
        String redirectUri = apiUrl + REDIRECT_URI_SUFFIX;

        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, clientId, clientSecret, SCOPES).build();

        GoogleTokenResponse response = flow.newTokenRequest(code)
                .setRedirectUri(redirectUri)
                .execute();

        CalendarIntegration integration = integrationRepository.findByProviderId(providerId)
                .orElse(CalendarIntegration.builder().providerId(providerId).build());

        integration.setAccessToken(response.getAccessToken());
        if (response.getRefreshToken() != null) {
            integration.setRefreshToken(response.getRefreshToken());
        }
        integration.setTokenExpiresAt(System.currentTimeMillis() + (response.getExpiresInSeconds() * 1000));
        
        integrationRepository.save(integration);
        log.info("✅ Google Calendar vinculado para Provider ID: {}", providerId);
        
        // Opcional: Sincronizar inmediatamente
        syncCalendar(providerId); 
    }

    /**
     * 3. Sincronizar Eventos (Importante: Crea TimeBlocks)
     */
    @Transactional
    public void syncCalendar(Long providerId) throws IOException {
        log.info("🔄 Sincronizando calendario para Provider ID: {}", providerId);

        CalendarIntegration integration = integrationRepository.findByProviderId(providerId)
                .orElseThrow(() -> new RuntimeException("Calendario no vinculado"));

        // Crear credencial usando el Refresh Token
        Credential credential = new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, clientId, clientSecret, SCOPES).build()
                .loadCredential(String.valueOf(providerId)); // Esto requiere un DataStore, pero para simplificar lo haremos manual abajo:
        
        // Manera manual de refrescar el token si usas BD propia:
        // (Aquí simplificamos asumiendo que el token es válido o regenerando el cliente)
        // Nota: Para producción robusta, implementa un CredentialStore propio.
        
        Calendar service = new Calendar.Builder(HTTP_TRANSPORT, JSON_FACTORY, request -> {
            request.getHeaders().setAuthorization("Bearer " + integration.getAccessToken());
        }).setApplicationName("QuHealthy").build();

        // Rango: Hoy a 30 días
        DateTime now = new DateTime(System.currentTimeMillis());
        DateTime future = new DateTime(System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000));

        Events events = service.events().list("primary")
                .setTimeMin(now)
                .setTimeMax(future)
                .setSingleEvents(true)
                .setOrderBy("startTime")
                .execute();

        List<Event> items = events.getItems();
        if (items.isEmpty()) {
            log.info("No hay eventos externos para sincronizar.");
            return;
        }

        for (Event event : items) {
            // Ignorar eventos transparentes (disponibles)
            if ("transparent".equals(event.getTransparency())) continue;
            if (event.getStart().getDateTime() == null) continue; // Ignorar eventos de todo el día por ahora

            // Mapeo a TimeBlock
            LocalDateTime start = LocalDateTime.ofInstant(Instant.ofEpochMilli(event.getStart().getDateTime().getValue()), ZoneId.systemDefault());
            LocalDateTime end = LocalDateTime.ofInstant(Instant.ofEpochMilli(event.getEnd().getDateTime().getValue()), ZoneId.systemDefault());

            // Buscar si ya existe este bloqueo (por externalId) o crear uno nuevo
            // (Asume que agregaste el campo 'externalId' a tu entidad TimeBlock)
            TimeBlock block = timeBlockRepository.findByExternalId(event.getId())
                    .orElse(TimeBlock.builder()
                            .providerId(providerId)
                            .reason("Google Calendar: " + (event.getSummary() != null ? "Ocupado" : "Ocupado")) // Privacidad: No guardar titulo real
                            .externalId(event.getId())
                            .isManual(false)
                            .build());

            block.setStartDateTime(start);
            block.setEndDateTime(end);
            
            timeBlockRepository.save(block);
        }
        log.info("✅ Sincronización finalizada. {} eventos procesados.", items.size());
    }
}
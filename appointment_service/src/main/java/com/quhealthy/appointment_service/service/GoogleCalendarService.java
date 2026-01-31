package com.quhealthy.appointment_service.service;

import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.http.BasicAuthentication;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.Events;
import com.quhealthy.appointment_service.model.CalendarIntegration;
import com.quhealthy.appointment_service.repository.CalendarIntegrationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class GoogleCalendarService {

    private final NetHttpTransport httpTransport;
    private final JsonFactory jsonFactory;
    private final CalendarIntegrationRepository integrationRepository; // ✅ Usamos tu repositorio

    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final String applicationName;

    private GoogleAuthorizationCodeFlow flow;

    public GoogleCalendarService(
            NetHttpTransport httpTransport,
            JsonFactory jsonFactory,
            CalendarIntegrationRepository integrationRepository,
            @Value("${google.calendar.client-id}") String clientId,
            @Value("${google.calendar.client-secret}") String clientSecret,
            @Value("${google.calendar.redirect-uri}") String redirectUri,
            @Value("${google.calendar.application-name:QuHealthy}") String applicationName) {

        this.httpTransport = httpTransport;
        this.jsonFactory = jsonFactory;
        this.integrationRepository = integrationRepository;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
        this.applicationName = applicationName;

        initFlow();
    }

    private void initFlow() {
        try {
            GoogleClientSecrets.Details details = new GoogleClientSecrets.Details();
            details.setClientId(clientId);
            details.setClientSecret(clientSecret);

            GoogleClientSecrets secrets = new GoogleClientSecrets();
            secrets.setWeb(details);

            this.flow = new GoogleAuthorizationCodeFlow.Builder(
                    httpTransport,
                    jsonFactory,
                    secrets,
                    Collections.singletonList(CalendarScopes.CALENDAR))
                    .setAccessType("offline")
                    .setApprovalPrompt("force")
                    .build();

            log.info("✅ Google Authorization Flow inicializado.");
        } catch (Exception e) {
            log.error("❌ Error fatal inicializando Google Calendar Service: {}", e.getMessage());
        }
    }

    public String getAuthorizationUrl(Long providerId) {
        return flow.newAuthorizationUrl()
                .setRedirectUri(redirectUri)
                .setState(String.valueOf(providerId))
                .build();
    }

    @Transactional
    public void exchangeCodeForTokens(String code, Long providerId) throws IOException {
        GoogleTokenResponse response = flow.newTokenRequest(code)
                .setRedirectUri(redirectUri)
                .execute();

        // 1. Buscamos si ya existe integración para actualizarla, o creamos una nueva
        CalendarIntegration integration = integrationRepository.findByProviderId(providerId)
                .orElse(CalendarIntegration.builder().providerId(providerId).build());

        // 2. Actualizamos los datos
        integration.setAccessToken(response.getAccessToken());
        
        // El refresh token solo viene la primera vez (o con approval_prompt=force)
        if (response.getRefreshToken() != null) {
            integration.setRefreshToken(response.getRefreshToken());
        }
        
        // Calculamos expiración (Ahora + segundos de vida * 1000)
        long expiresInMillis = response.getExpiresInSeconds() * 1000;
        integration.setTokenExpiresAt(System.currentTimeMillis() + expiresInMillis);

        integrationRepository.save(integration);
        log.info("💾 Integración guardada exitosamente para Provider ID: {}", providerId);
    }

    @Transactional
    public List<Event> syncCalendar(Long providerId) throws IOException {
        // 1. Obtener integración de TU repositorio
        CalendarIntegration integration = integrationRepository.findByProviderId(providerId)
                .orElseThrow(() -> new IllegalArgumentException("El proveedor no ha conectado su calendario."));

        // 2. Reconstruir Credencial
        Credential credential = new Credential.Builder(BearerToken.authorizationHeaderAccessMethod())
                .setTransport(httpTransport)
                .setJsonFactory(jsonFactory)
                .setTokenServerUrl(new GenericUrl("https://oauth2.googleapis.com/token"))
                .setClientAuthentication(new BasicAuthentication(clientId, clientSecret))
                .build();

        credential.setAccessToken(integration.getAccessToken());
        credential.setRefreshToken(integration.getRefreshToken());
        
        // Seteamos expiración para que la librería sepa si debe renovar
        Long expiresAt = integration.getTokenExpiresAt();
        if (expiresAt != null) {
            long expiresInSeconds = (expiresAt - System.currentTimeMillis()) / 1000;
            if (expiresInSeconds > 0) {
                credential.setExpiresInSeconds(expiresInSeconds);
            }
        }

        // 3. Crear cliente
        Calendar service = new Calendar.Builder(httpTransport, jsonFactory, credential)
                .setApplicationName(applicationName)
                .build();

        // 4. Llamada REAL a Google
        DateTime now = new DateTime(System.currentTimeMillis());
        DateTime oneWeekLater = new DateTime(System.currentTimeMillis() + (7 * 24 * 60 * 60 * 1000)); // 7 días

        log.info("🔄 Sincronizando calendario: {}", integration.getCalendarId());
        
        Events events = service.events().list(integration.getCalendarId()) // Usamos el ID guardado (default 'primary')
                .setTimeMin(now)
                .setTimeMax(oneWeekLater)
                .setOrderBy("startTime")
                .setSingleEvents(true)
                .execute();

        // 5. Guardar nuevos tokens si hubo refresh automático
        if (credential.getAccessToken() != null && !credential.getAccessToken().equals(integration.getAccessToken())) {
            integration.setAccessToken(credential.getAccessToken());
            if (credential.getExpiresInSeconds() != null) {
                integration.setTokenExpiresAt(System.currentTimeMillis() + (credential.getExpiresInSeconds() * 1000));
            }
            integrationRepository.save(integration);
            log.info("🔄 Tokens renovados automáticamente y guardados.");
        }

        return events.getItems();
    }
}
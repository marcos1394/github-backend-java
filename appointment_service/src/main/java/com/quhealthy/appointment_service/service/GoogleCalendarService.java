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

@Slf4j
@Service
public class GoogleCalendarService {

    private final NetHttpTransport httpTransport;
    private final JsonFactory jsonFactory;
    private final CalendarIntegrationRepository integrationRepository;

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
            // Validación preventiva para evitar NullPointerExceptions
            if (clientId == null || clientSecret == null || clientId.isBlank() || clientSecret.isBlank()) {
                log.error("⚠️ CLAVES FALTANTES: Google Calendar Client ID o Secret no están configurados.");
                return;
            }

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

            log.info("✅ Google Authorization Flow inicializado correctamente.");
            
        } catch (Throwable e) { 
            // 🛡️ BLINDAJE CRÍTICO: Capturamos Throwable para atrapar errores de librerías (LinkageError)
            // Esto permite que Spring arranque aunque el calendario falle internamente.
            log.error("❌ ERROR FATAL al iniciar servicio de Calendario (La app continuará sin esta función): ", e);
        }
    }

    public String getAuthorizationUrl(Long providerId) {
        if (flow == null) throw new IllegalStateException("El servicio de Google Calendar no se pudo iniciar. Revisa los logs.");
        
        return flow.newAuthorizationUrl()
                .setRedirectUri(redirectUri)
                .setState(String.valueOf(providerId))
                .build();
    }

    @Transactional
    public void exchangeCodeForTokens(String code, Long providerId) throws IOException {
        if (flow == null) throw new IllegalStateException("Servicio de calendario no disponible.");

        GoogleTokenResponse response = flow.newTokenRequest(code)
                .setRedirectUri(redirectUri)
                .execute();

        // 1. Buscamos si ya existe integración o creamos una nueva
        CalendarIntegration integration = integrationRepository.findByProviderId(providerId)
                .orElse(CalendarIntegration.builder().providerId(providerId).build());

        // 2. Actualizamos los datos
        integration.setAccessToken(response.getAccessToken());
        
        // El refresh token solo viene la primera vez
        if (response.getRefreshToken() != null) {
            integration.setRefreshToken(response.getRefreshToken());
        }
        
        // Calculamos expiración
        if (response.getExpiresInSeconds() != null) {
            long expiresInMillis = response.getExpiresInSeconds() * 1000;
            integration.setTokenExpiresAt(System.currentTimeMillis() + expiresInMillis);
        }

        integrationRepository.save(integration);
        log.info("💾 Integración guardada exitosamente para Provider ID: {}", providerId);
    }

    @Transactional
    public List<Event> syncCalendar(Long providerId) throws IOException {
        if (flow == null) throw new IllegalStateException("Servicio de calendario no disponible.");

        // 1. Obtener integración
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
        
        // Seteamos expiración
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

        log.info("🔄 Sincronizando calendario ID: {}", integration.getCalendarId());
        
        Events events = service.events().list(integration.getCalendarId())
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
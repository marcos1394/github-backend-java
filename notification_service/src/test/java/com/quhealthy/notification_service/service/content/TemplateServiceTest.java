package com.quhealthy.notification_service.service.content;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TemplateServiceTest {

    @Mock
    private TemplateEngine templateEngine; // Mockeamos el motor de Thymeleaf

    @InjectMocks
    private TemplateService templateService;

    // =========================================================================
    // ✅ ESCENARIO 1: RENDERIZADO EXITOSO
    // =========================================================================

    @Test
    @DisplayName("Debe procesar el template inyectando las variables correctamente")
    void shouldProcessTemplateWithVariables() {
        // Arrange
        String templateName = "welcome-email";
        Map<String, Object> variables = Map.of("name", "Juan", "role", "ADMIN");
        String expectedHtml = "<html>Hola Juan</html>";

        // Simulamos que Thymeleaf hace su trabajo y devuelve un String
        when(templateEngine.process(eq(templateName), any(Context.class)))
                .thenReturn(expectedHtml);

        // Act
        String result = templateService.generateContent(templateName, variables);

        // Assert
        assertThat(result).isEqualTo(expectedHtml);

        // 🕵️ VERIFICACIÓN PROFUNDA (ArgumentCaptor):
        // Verificamos que el mapa de variables llegó correctamente al Contexto de Thymeleaf
        ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);
        verify(templateEngine).process(eq(templateName), contextCaptor.capture());

        Context capturedContext = contextCaptor.getValue();
        assertThat(capturedContext.getVariable("name")).isEqualTo("Juan");
        assertThat(capturedContext.getVariable("role")).isEqualTo("ADMIN");
    }

    // =========================================================================
    // 🛡️ ESCENARIO 2: MANEJO DE NULOS (DEFENSIVE PROGRAMMING)
    // =========================================================================

    @Test
    @DisplayName("Debe manejar variables nulas creando un contexto vacío sin romper")
    void shouldHandleNullVariables() {
        // Arrange
        String templateName = "simple-alert";
        when(templateEngine.process(anyString(), any(Context.class))).thenReturn("<div>Alert</div>");

        // Act
        String result = templateService.generateContent(templateName, null);

        // Assert
        assertThat(result).isEqualTo("<div>Alert</div>");

        // Verificamos que el contexto no es nulo, sino que está vacío
        ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);
        verify(templateEngine).process(eq(templateName), contextCaptor.capture());

        assertThat(contextCaptor.getValue().getVariableNames()).isEmpty();
    }

    @Test
    @DisplayName("Debe lanzar IllegalArgumentException si el nombre del template está vacío")
    void shouldThrowExceptionIfTemplateNameIsEmpty() {
        assertThatThrownBy(() -> templateService.generateContent("", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("El nombre del template no puede estar vacío");

        assertThatThrownBy(() -> templateService.generateContent(null, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // =========================================================================
    // ❌ ESCENARIO 3: ERRORES DEL MOTOR (THYMELEAF)
    // =========================================================================

    @Test
    @DisplayName("Debe envolver excepciones de Thymeleaf en RuntimeException")
    void shouldWrapThymeleafExceptions() {
        // Arrange
        String templateName = "missing-file";
        // Simulamos que Thymeleaf no encuentra el archivo .html
        when(templateEngine.process(anyString(), any(Context.class)))
                .thenThrow(new RuntimeException("Template not found in resources"));

        // Act & Assert
        assertThatThrownBy(() -> templateService.generateContent(templateName, Map.of()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Error procesando template de email") // El mensaje que pusiste en tu catch
                .hasRootCauseMessage("Template not found in resources"); // La causa original
    }
}
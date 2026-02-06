package com.quhealthy.onboarding_service.service.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.types.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class GeminiKycService {

    private final Client client;
    private final String modelName;
    private final ObjectMapper objectMapper;

    public GeminiKycService(
            @Value("${gemini.api-key}") String apiKey,
            @Value("${gemini.model}") String modelName,
            ObjectMapper objectMapper) {
        this.client = Client.builder().apiKey(apiKey).build();
        this.modelName = modelName;
        this.objectMapper = objectMapper;
    }

    /**
     * Metodo 1: OCR e Inteligencia Documental.
     * Extrae texto (Nombre, CURP) y valida seguridad del documento.
     */
    public Map<String, Object> extractIdentityData(MultipartFile file, String documentType) {
        log.info("Analizando documento {} con Gemini...", documentType);

        try {
            String base64Image = Base64.getEncoder().encodeToString(file.getBytes());

            String promptText = """
                Analiza esta imagen (%s). Extrae en JSON:
                - nombre_completo (String)
                - curp (String)
                - fecha_nacimiento (YYYY-MM-DD)
                - es_legible (Boolean)
                - parece_alterado (Boolean)
                Si no es visible, usa null.
                """.formatted(documentType);

            Part textPart = Part.builder().text(promptText).build();
            Part imagePart = Part.builder()
                    .inlineData(Blob.builder()
                            .mimeType(file.getContentType())
                            .data(base64Image.getBytes())
                            .build())
                    .build();

            GenerateContentConfig config = GenerateContentConfig.builder()
                    .responseMimeType("application/json")
                    .temperature(0.0f)
                    .build();

            GenerateContentResponse response = client.models.generateContent(
                    modelName,
                    Content.builder().parts(List.of(imagePart, textPart)).build(),
                    config
            );

            return objectMapper.readValue(response.text(), new TypeReference<Map<String, Object>>() {});

        } catch (Exception e) {
            log.error("Error en Gemini KYC (OCR): {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * Metodo 2: Biometria Facial (Face Match).
     * Compara la Selfie (en vivo) contra la foto del ID (guardada).
     */
    public Map<String, Object> verifyBiometricMatch(MultipartFile selfieFile, byte[] idDocumentBytes) {
        log.info("Iniciando validacion biometrica (Selfie vs ID)...");

        try {
            // 1. Preparar Selfie (Imagen A)
            String base64Selfie = Base64.getEncoder().encodeToString(selfieFile.getBytes());
            Part partSelfie = Part.builder()
                    .inlineData(Blob.builder()
                            .mimeType(selfieFile.getContentType())
                            .data(base64Selfie.getBytes())
                            .build())
                    .build();

            // 2. Preparar Documento ID (Imagen B) - Asumimos JPEG/PNG
            String base64Id = Base64.getEncoder().encodeToString(idDocumentBytes);
            Part partIdDoc = Part.builder()
                    .inlineData(Blob.builder()
                            .mimeType("image/jpeg")
                            .data(base64Id.getBytes())
                            .build())
                    .build();

            // 3. Prompt de Seguridad Biometrica
            String promptText = """
                Actua como un experto forense. Compara a la persona de la 'Imagen 1' (Selfie) con la foto del rostro en la 'Imagen 2' (ID).
                Responde estrictamente en JSON:
                - is_same_person (Boolean): ¿Es la misma persona?
                - confidence_score (Integer 0-100): Certeza.
                - liveness_check (String): "PASSED" si parece una foto real, "FAILED" si es foto de pantalla/papel.
                - reasoning (String): Breve explicacion.
                """;

            Part textPart = Part.builder().text(promptText).build();

            // 4. Configurar
            GenerateContentConfig config = GenerateContentConfig.builder()
                    .responseMimeType("application/json")
                    .temperature(0.0f)
                    .build();

            // 5. Ejecutar Comparacion
            GenerateContentResponse response = client.models.generateContent(
                    modelName,
                    Content.builder().parts(List.of(partSelfie, partIdDoc, textPart)).build(),
                    config
            );

            return objectMapper.readValue(response.text(), new TypeReference<Map<String, Object>>() {});

        } catch (Exception e) {
            log.error("Error en Gemini Biometrics: {}", e.getMessage());
            // Retornamos fallo por defecto en caso de error técnico para no abrir brechas de seguridad
            return Map.of(
                    "is_same_person", false,
                    "liveness_check", "FAILED",
                    "reasoning", "Error tecnico al procesar imagenes"
            );
        }
    }

    /**
     * Extrae datos de una Cédula Profesional Mexicana.
     */
    public Map<String, Object> extractLicenseData(MultipartFile file) {
        log.info("Analizando Cedula Profesional con Gemini...");

        try {
            String base64Image = Base64.getEncoder().encodeToString(file.getBytes());

            // Prompt especializado para Cédulas (verdes o QR)
            String promptText = """
                Analiza esta imagen de una Cedula Profesional Mexicana.
                Extrae estrictamente en JSON:
                - numero_cedula (String): El numero principal.
                - nombre_titular (String): Nombre completo del profesional.
                - profesion (String): Ej: Medico Cirujano, Licenciado en Nutricion.
                - institucion (String): Universidad emisora.
                - es_legible (Boolean)
                - documento_valido (Boolean): ¿Parece un documento oficial real y no una falsificacion obvia?
                """;

            Part textPart = Part.builder().text(promptText).build();
            Part imagePart = Part.builder()
                    .inlineData(Blob.builder()
                            .mimeType(file.getContentType())
                            .data(base64Image.getBytes())
                            .build())
                    .build();

            GenerateContentConfig config = GenerateContentConfig.builder()
                    .responseMimeType("application/json")
                    .temperature(0.0f)
                    .build();

            GenerateContentResponse response = client.models.generateContent(
                    modelName,
                    Content.builder().parts(List.of(imagePart, textPart)).build(),
                    config
            );

            return objectMapper.readValue(response.text(), new TypeReference<Map<String, Object>>() {});

        } catch (Exception e) {
            log.error("Error analizando Cedula: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }
}
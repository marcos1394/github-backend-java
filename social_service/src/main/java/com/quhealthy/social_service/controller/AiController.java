package com.quhealthy.social_service.controller;

import com.quhealthy.social_service.dto.ai.AiImageRequest;
import com.quhealthy.social_service.dto.ai.AiTextRequest;
import com.quhealthy.social_service.dto.ai.AiVideoRequest;
import com.quhealthy.social_service.dto.ai.AiVideoResponse;
import com.quhealthy.social_service.service.ai.ContentGeneratorService;
import com.quhealthy.social_service.service.ai.ImageGeneratorService;
import com.quhealthy.social_service.service.ai.VideoGeneratorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/social/ai")
@RequiredArgsConstructor
public class AiController {

    private final ContentGeneratorService contentGeneratorService;
    private final ImageGeneratorService imageGeneratorService;
    private final VideoGeneratorService videoGeneratorService;

    // ✅ GENERACIÓN DE TEXTO (INTACTO)
    @PostMapping("/generate-text")
    public ResponseEntity<?> generateText(@RequestBody AiTextRequest request) {
        log.info("📝 Solicitud de generación de texto recibida.");
        try {
            var response = contentGeneratorService.generatePostText(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("❌ Error generando texto: ", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Error generando texto con IA: " + e.getMessage()));
        }
    }

    // ✅ GENERACIÓN DE IMAGEN (INTACTO)
    @PostMapping("/generate-image")
    public ResponseEntity<?> generateImage(@RequestBody AiImageRequest request) {
        log.info("🎨 Solicitud de generación de imagen recibida.");
        try {
            var response = imageGeneratorService.generateImage(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("❌ Error Image Gen: ", e);
            return ResponseEntity.internalServerError().body(buildErrorMap(e));
        }
    }

    // ✅ GENERACIÓN DE VIDEO (CORREGIDO PARA MOSTRAR ERRORES REALES)
    @PostMapping("/generate-video")
    public ResponseEntity<?> generateVideo(@Valid @RequestBody AiVideoRequest request) {
        log.info("🎬 Solicitud de generación de video recibida.");
        try {
            // Nota: Debido al Polling en el servicio, esto mantendrá la conexión abierta
            // hasta que el video esté listo (aprox 60-90 segundos).
            AiVideoResponse response = videoGeneratorService.generateVideo(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("❌ Error Video Gen (Veo): ", e);
            // AHORA SÍ: Devolvemos el error real de Google/SDK
            return ResponseEntity.internalServerError().body(buildErrorMap(e));
        }
    }

    // 🛠️ MÉTODO AUXILIAR
    private Map<String, String> buildErrorMap(Exception e) {
        Map<String, String> errorDetails = new HashMap<>();
        errorDetails.put("exception", e.getClass().getSimpleName());
        errorDetails.put("message", e.getMessage());
        if (e.getCause() != null) {
            errorDetails.put("cause", e.getCause().getMessage());
        }
        return errorDetails;
    }
}
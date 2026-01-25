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

    // ✅ GENERACIÓN DE TEXTO (INTACTO - NO TOCAR)
    @PostMapping("/generate-text")
    public ResponseEntity<?> generateText(@RequestBody AiTextRequest request) {
        log.info("📝 Solicitud de generación de texto recibida.");
        try {
            // ✅ Ahora capturamos cualquier error que venga de la IA
            var response = contentGeneratorService.generatePostText(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("❌ Error generando texto: ", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Error generando texto con IA: " + e.getMessage()));
        }
    }

    // ✅ GENERACIÓN DE IMAGEN (CORREGIDO)
    @PostMapping("/generate-image")
    public ResponseEntity<?> generateImage(@RequestBody AiImageRequest request) {
        log.info("🎨 Solicitud de generación de imagen recibida.");
        try {
            var response = imageGeneratorService.generateImage(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("❌ Error Image Gen: ", e);
            // Retornamos el error crudo usando el método auxiliar de abajo
            return ResponseEntity.internalServerError().body(buildErrorMap(e));
        }
    }

    // ✅ GENERACIÓN DE VIDEO (MANTENIDO IGUAL)
    @PostMapping("/generate-video")
    public ResponseEntity<AiVideoResponse> generateVideo(@Valid @RequestBody AiVideoRequest request) {
        log.info("🎬 Solicitud de generación de video recibida.");
        try {
            // Nota: Esto puede tardar 10-20 segundos.
            AiVideoResponse response = videoGeneratorService.generateVideo(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error generando video con Veo: ", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // 🛠️ MÉTODO AUXILIAR (ESTE ES EL QUE FALTABA PARA COMPILAR)
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

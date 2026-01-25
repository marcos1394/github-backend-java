package com.quhealthy.social_service.controller;

import com.quhealthy.social_service.dto.ai.AiTextRequest;
import com.quhealthy.social_service.dto.ai.AiTextResponse;
import com.quhealthy.social_service.service.ai.ContentGeneratorService;
import com.quhealthy.social_service.dto.ai.AiImageRequest;
import com.quhealthy.social_service.dto.ai.AiImageResponse;
import com.quhealthy.social_service.service.ai.ImageGeneratorService;
import com.quhealthy.social_service.dto.ai.AiVideoRequest;
import com.quhealthy.social_service.dto.ai.AiVideoResponse;
import com.quhealthy.social_service.service.ai.VideoGeneratorService;
import jakarta.validation.Valid;
import java.util.Map; // 👈 ESTO FALTABA (Error 'cannot find symbol variable Map')
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@Slf4j
@RestController
@RequestMapping("/api/social/ai")
@RequiredArgsConstructor
public class AiController {

    private final ContentGeneratorService contentGeneratorService;
    private final ImageGeneratorService imageGeneratorService;
    private final VideoGeneratorService videoGeneratorService;

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

    @PostMapping("/generate-image")
    public ResponseEntity<?> generateImage(@RequestBody AiImageRequest request) {
        log.info("🎨 Solicitud de generación de imagen recibida.");
        
        // 🚨 AQUÍ ESTABA EL ERROR: Faltaba el try-catch
        try {
            var response = imageGeneratorService.generateImage(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("❌ Error generando imagen: ", e);
            // Devolvemos un 500 limpio al frontend
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Error generando imagen: " + e.getMessage()));
        }
    }
    

    @PostMapping("/generate-video")
    public ResponseEntity<AiVideoResponse> generateVideo(@Valid @RequestBody AiVideoRequest request) {
        try {
            // Nota: Esto puede tardar 10-20 segundos. 
            // En un frontend real, mostraríamos un "loading" spinner largo.
            AiVideoResponse response = videoGeneratorService.generateVideo(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error generando video con Veo: ", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
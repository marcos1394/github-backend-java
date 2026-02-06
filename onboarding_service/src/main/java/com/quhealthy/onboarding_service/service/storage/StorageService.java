package com.quhealthy.onboarding_service.service.storage;

import org.springframework.web.multipart.MultipartFile;

public interface StorageService {

    /**
     * Sube un archivo al almacenamiento configurado.
     * @param file El archivo binario recibido.
     * @param userId El ID del usuario (para organizar carpetas).
     * @param documentType Tipo de documento (INE_FRONT, PASSPORT, etc.).
     * @return El 'key' o ruta relativa del archivo guardado.
     */
    String uploadFile(MultipartFile file, Long userId, String documentType);

    /**
     * Genera una URL temporal firmada para ver el archivo de forma segura.
     * @param fileKey El path del archivo en el bucket.
     * @return URL completa firmada.
     */
    String getPresignedUrl(String fileKey);

    /**
     * Descarga los bytes del archivo desde el almacenamiento.
     * NECESARIO para que Gemini pueda comparar la Selfie contra la INE guardada.
     * @param fileKey El path del archivo en el bucket.
     * @return Array de bytes de la imagen.
     */
    byte[] getFileBytes(String fileKey);
}
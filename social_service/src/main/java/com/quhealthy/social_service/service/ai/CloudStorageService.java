package com.quhealthy.social_service.service.ai;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CloudStorageService {

    private final Storage storage;

    @Value("${spring.cloud.gcp.storage.bucket-name:quhealthy-media}") // Nombre del bucket en properties
    private String bucketName;

    /**
     * Sube un archivo (bytes) a Google Cloud Storage y devuelve la URL pública.
     */
    public String uploadFile(byte[] fileData, String contentType, String folder) {
        String fileName = folder + "/" + UUID.randomUUID().toString() + getExtension(contentType);
        
        BlobId blobId = BlobId.of(bucketName, fileName);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                .setContentType(contentType)
                .build();

        // Subir archivo
        Blob blob = storage.create(blobInfo, fileData);

        // Devolver URL pública (asumiendo que el bucket es público o usamos URLs firmadas)
        // Para este caso, usamos la URL directa de media link
        return "https://storage.googleapis.com/" + bucketName + "/" + fileName;
    }

    private String getExtension(String contentType) {
        if (contentType == null) return ".jpg";
        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/jpeg", "image/jpg" -> ".jpg";
            case "video/mp4" -> ".mp4";
            default -> ".bin";
        };
    }
}
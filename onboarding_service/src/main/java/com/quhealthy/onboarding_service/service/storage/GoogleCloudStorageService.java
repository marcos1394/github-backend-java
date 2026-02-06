package com.quhealthy.onboarding_service.service.storage;

import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.net.URL;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@ConditionalOnProperty(name = "storage.provider", havingValue = "gcp", matchIfMissing = true)
@RequiredArgsConstructor
public class GoogleCloudStorageService implements StorageService {

    private final Storage storage;

    @Value("${gcp.bucket-name}")
    private String bucketName;

    @Override
    public String uploadFile(MultipartFile file, Long userId, String documentType) {
        try {
            String extension = getExtension(file.getOriginalFilename());
            // Estructura: providers/101/INE_FRONT-a1b2c3d4.jpg
            String fileName = String.format("providers/%d/%s-%s.%s",
                    userId, documentType, UUID.randomUUID(), extension);

            log.info("Subiendo archivo a Google Cloud Storage: {}", fileName);

            BlobInfo blobInfo = BlobInfo.newBuilder(bucketName, fileName)
                    .setContentType(file.getContentType())
                    .build();

            storage.create(blobInfo, file.getBytes());

            log.info("Archivo subido exitosamente a GCP: {}", fileName);
            return fileName;

        } catch (Exception e) {
            log.error("Error crítico subiendo a GCP: {}", e.getMessage());
            throw new RuntimeException("Error al subir documento a la nube: " + e.getMessage());
        }
    }

    @Override
    public String getPresignedUrl(String fileKey) {
        try {
            // Genera una URL valida por 15 minutos
            // NOTA: La Service Account debe tener permiso 'Service Account Token Creator'
            URL signedUrl = storage.signUrl(
                    BlobInfo.newBuilder(bucketName, fileKey).build(),
                    15,
                    TimeUnit.MINUTES,
                    Storage.SignUrlOption.withV4Signature()
            );
            return signedUrl.toString();
        } catch (Exception e) {
            log.error("Error generando URL firmada en GCP: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public byte[] getFileBytes(String fileKey) {
        try {
            log.debug("Descargando bytes desde GCP: {}", fileKey);
            return storage.readAllBytes(bucketName, fileKey);
        } catch (Exception e) {
            log.error("Error leyendo archivo de GCP (Face Match fallará): {}", fileKey, e);
            throw new RuntimeException("No se pudo leer el documento de identidad para validación biométrica.");
        }
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "jpg";
        return filename.substring(filename.lastIndexOf(".") + 1);
    }
}
package com.quhealthy.onboarding_service.service.storage;

import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@ConditionalOnProperty(name = "storage.provider", havingValue = "minio")
@RequiredArgsConstructor
public class MinioStorageService implements StorageService {

    private final MinioClient minioClient;

    @Value("${minio.bucket-name}")
    private String bucketName;

    @Override
    public String uploadFile(MultipartFile file, Long userId, String documentType) {
        String extension = getExtension(file.getOriginalFilename());
        String fileName = String.format("providers/%d/%s-%s.%s",
                userId, documentType, UUID.randomUUID(), extension);

        try (InputStream inputStream = file.getInputStream()) {
            log.info("Subiendo archivo a MinIO Local: {}", fileName);

            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(fileName)
                            .stream(inputStream, file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );

            log.info("Archivo subido a MinIO: {}", fileName);
            return fileName;

        } catch (Exception e) {
            log.error("Error subiendo a MinIO: {}", e.getMessage());
            throw new RuntimeException("Error almacenando archivo localmente.");
        }
    }

    @Override
    public String getPresignedUrl(String fileKey) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucketName)
                            .object(fileKey)
                            .expiry(15, TimeUnit.MINUTES)
                            .build()
            );
        } catch (Exception e) {
            log.error("Error generando URL MinIO: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public byte[] getFileBytes(String fileKey) {
        try (InputStream stream = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucketName)
                        .object(fileKey)
                        .build())) {

            log.debug("Descargando bytes desde MinIO: {}", fileKey);
            return stream.readAllBytes();

        } catch (Exception e) {
            log.error("Error leyendo archivo de MinIO (Face Match fallará): {}", fileKey, e);
            throw new RuntimeException("No se pudo leer el documento de identidad localmente.");
        }
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "jpg";
        return filename.substring(filename.lastIndexOf(".") + 1);
    }
}
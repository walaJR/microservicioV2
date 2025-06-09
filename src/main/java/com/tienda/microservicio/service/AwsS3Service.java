package com.tienda.microservicio.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.tienda.microservicio.dto.S3FileInfo;
import com.tienda.microservicio.dto.UploadResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.GetUrlRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;

@Service
@RequiredArgsConstructor
@Slf4j
public class AwsS3Service {

    private final S3Client s3Client;

    @Value("${aws.s3.bucket.name}")
    private String bucketName;

    /**
     * Sube un archivo al bucket S3
     */
    public UploadResponse uploadFile(MultipartFile file) {
        try {
            // Generar nombre único para el archivo
            String originalFileName = file.getOriginalFilename();
            String fileName = generateUniqueFileName(originalFileName);
            String contentType = file.getContentType();

            // Crear request de subida
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileName)
                    .contentType(contentType)
                    .contentLength(file.getSize())
                    .build();

            // Subir archivo
            s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

            // Generar URL del archivo
            String fileUrl = getFileUrl(fileName);

            log.info("Archivo subido exitosamente: {}", fileName);

            return new UploadResponse(
                    "Archivo subido exitosamente",
                    fileName,
                    fileUrl,
                    file.getSize(),
                    true);

        } catch (IOException e) {
            log.error("Error al leer el archivo: {}", e.getMessage());
            return new UploadResponse(
                    "Error al leer el archivo: " + e.getMessage(),
                    null,
                    null,
                    0,
                    false);
        } catch (S3Exception e) {
            log.error("Error de S3 al subir archivo: {}", e.getMessage());
            return new UploadResponse(
                    "Error de S3: " + e.getMessage(),
                    null,
                    null,
                    0,
                    false);
        }
    }

    /**
     * Lista todos los archivos del bucket
     */
    public List<S3FileInfo> listFiles() {
        try {
            ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
                    .bucket(bucketName)
                    .build();

            ListObjectsV2Response listResponse = s3Client.listObjectsV2(listRequest);

            return listResponse.contents().stream()
                    .map(this::convertToS3FileInfo)
                    .collect(Collectors.toList());

        } catch (S3Exception e) {
            log.error("Error al listar archivos: {}", e.getMessage());
            throw new RuntimeException("Error al listar archivos: " + e.getMessage());
        }
    }

    /**
     * Descarga un archivo del bucket
     */
    public byte[] downloadFile(String fileName) {
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileName)
                    .build();

            ResponseInputStream<GetObjectResponse> s3Object = s3Client.getObject(getObjectRequest);

            return readAllBytes(s3Object);

        } catch (NoSuchKeyException e) {
            log.error("Archivo no encontrado: {}", fileName);
            throw new RuntimeException("Archivo no encontrado: " + fileName);
        } catch (S3Exception e) {
            log.error("Error de S3 al descargar archivo: {}", e.getMessage());
            throw new RuntimeException("Error de S3: " + e.getMessage());
        } catch (IOException e) {
            log.error("Error al leer el archivo descargado: {}", e.getMessage());
            throw new RuntimeException("Error al leer el archivo: " + e.getMessage());
        }
    }

    /**
     * Elimina un archivo del bucket
     */
    public boolean deleteFile(String fileName) {
        try {
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileName)
                    .build();

            s3Client.deleteObject(deleteRequest);
            log.info("Archivo eliminado exitosamente: {}", fileName);
            return true;

        } catch (S3Exception e) {
            log.error("Error al eliminar archivo: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Verifica si un archivo existe
     */
    public boolean fileExists(String fileName) {
        try {
            HeadObjectRequest headRequest = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileName)
                    .build();

            s3Client.headObject(headRequest);
            return true;

        } catch (NoSuchKeyException e) {
            return false;
        } catch (S3Exception e) {
            log.error("Error al verificar existencia del archivo: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Obtiene información detallada de un archivo
     */
    public S3FileInfo getFileInfo(String fileName) {
        try {
            HeadObjectRequest headRequest = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileName)
                    .build();

            HeadObjectResponse headResponse = s3Client.headObject(headRequest);

            return new S3FileInfo(
                    extractFileName(fileName),
                    fileName,
                    headResponse.contentLength(),
                    headResponse.lastModified(),
                    headResponse.contentType(),
                    getFileUrl(fileName));

        } catch (NoSuchKeyException e) {
            throw new RuntimeException("Archivo no encontrado: " + fileName);
        } catch (S3Exception e) {
            throw new RuntimeException("Error de S3: " + e.getMessage());
        }
    }

    // Métodos auxiliares privados

    private String generateUniqueFileName(String originalFileName) {
        String extension = "";
        if (originalFileName != null && originalFileName.contains(".")) {
            extension = originalFileName.substring(originalFileName.lastIndexOf("."));
        }
        return UUID.randomUUID().toString() + extension;
    }

    private String getFileUrl(String fileName) {
        try {
            GetUrlRequest getUrlRequest = GetUrlRequest.builder()
                    .bucket(bucketName)
                    .key(fileName)
                    .build();

            return s3Client.utilities().getUrl(getUrlRequest).toString();
        } catch (Exception e) {
            // Fallback URL construction
            return String.format("https://%s.s3.amazonaws.com/%s",
                    bucketName, URLEncoder.encode(fileName, StandardCharsets.UTF_8));
        }
    }

    private S3FileInfo convertToS3FileInfo(S3Object s3Object) {
        return new S3FileInfo(
                extractFileName(s3Object.key()),
                s3Object.key(),
                s3Object.size(),
                s3Object.lastModified(),
                null, // Content type no disponible en listado
                getFileUrl(s3Object.key()));
    }

    private String extractFileName(String key) {
        return key.contains("/") ? key.substring(key.lastIndexOf("/") + 1) : key;
    }

    private byte[] readAllBytes(ResponseInputStream<GetObjectResponse> inputStream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[1024];
        int bytesRead;

        while ((bytesRead = inputStream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, bytesRead);
        }

        inputStream.close();
        return buffer.toByteArray();
    }
}
package com.collab.service;

import com.collab.common.enums.MediaFileType;
import com.collab.entity.MediaFile;
import com.collab.entity.User;
import com.collab.repository.MediaFileRepository;
import io.minio.*;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class MinioService {

    private final MinioClient minioClient;
    private final MediaFileRepository mediaFileRepository;

    @Value("${minio.bucket}")
    private String bucket;

    @Value("${minio.url}")
    private String publicUrl;

    /**
     * Загружает файл в MinIO и сохраняет метаданные в БД.
     *
     * Структура ключей:
     *   USER_AVATAR      → avatars/{userId}/{uuid}.jpg
     *   TASK_COVER       → tasks/{taskId}/{uuid}.jpg
     *   PORTFOLIO        → portfolio/{userId}/{uuid}.mp4
     *   WORK_SUBMISSION  → submissions/{applicationId}/{uuid}.mp4
     *   CHAT_ATTACHMENT  → chat/{applicationId}/{uuid}.pdf
     */
    @Transactional
    public MediaFile upload(MultipartFile file, MediaFileType type, UUID entityId, User uploader) {
        String ext = getExtension(file.getOriginalFilename());
        String objectKey = buildKey(type, entityId, ext);

        try {
            ensureBucketExists();

            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());

        } catch (Exception e) {
            log.error("Failed to upload file to MinIO: {}", e.getMessage(), e);
            throw new RuntimeException("File upload failed: " + e.getMessage());
        }

        MediaFile media = MediaFile.builder()
                .uploader(uploader)
                .bucket(bucket)
                .objectKey(objectKey)
                .originalFilename(file.getOriginalFilename())
                .contentType(file.getContentType())
                .sizeBytes(file.getSize())
                .publicUrl(publicUrl + "/" + bucket + "/" + objectKey)
                .entityType(type)
                .entityId(entityId)
                .build();

        return mediaFileRepository.save(media);
    }

    /**
     * Генерирует временную подписанную ссылку (presigned URL) — для приватных файлов.
     * Ссылка действует 1 час.
     */
    public String getPresignedUrl(String objectKey) {
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .method(Method.GET)
                    .expiry(1, TimeUnit.HOURS)
                    .build());
        } catch (Exception e) {
            log.error("Failed to get presigned URL: {}", e.getMessage(), e);
            throw new RuntimeException("Could not generate file URL");
        }
    }

    /**
     * Удаляет файл из MinIO и из БД.
     */
    @Transactional
    public void delete(String objectKey) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build());
        } catch (Exception e) {
            log.warn("Failed to delete from MinIO (maybe already deleted): {}", objectKey);
        }
        mediaFileRepository.deleteByObjectKey(objectKey);
    }

    // ---- helpers ----

    private String buildKey(MediaFileType type, UUID entityId, String ext) {
        String prefix = switch (type) {
            case USER_AVATAR -> "avatars/" + entityId;
            case TASK_COVER -> "tasks/" + entityId;
            case PORTFOLIO -> "portfolio/" + entityId;
            case WORK_SUBMISSION -> "submissions/" + entityId;
            case CHAT_ATTACHMENT -> "chat/" + entityId;
            case BRAND_LOGO -> "brands/" + entityId;
        };
        return prefix + "/" + UUID.randomUUID() + ext;
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf("."));
    }

    private void ensureBucketExists() throws Exception {
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            // Делаем bucket публичным для чтения (аватары, обложки задач)
            String policy = """
                {
                  "Version": "2012-10-17",
                  "Statement": [{
                    "Effect": "Allow",
                    "Principal": {"AWS": ["*"]},
                    "Action": ["s3:GetObject"],
                    "Resource": ["arn:aws:s3:::%s/*"]
                  }]
                }
                """.formatted(bucket);
            minioClient.setBucketPolicy(SetBucketPolicyArgs.builder()
                    .bucket(bucket).config(policy).build());
            log.info("Created MinIO bucket: {}", bucket);
        }
    }
}

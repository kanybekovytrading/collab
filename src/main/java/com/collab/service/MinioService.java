package com.collab.service;

import com.collab.common.enums.MediaFileType;
import com.collab.entity.MediaFile;
import com.collab.entity.User;
import com.collab.repository.MediaFileRepository;
import io.minio.*;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;
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

    @PostConstruct
    public void initBucketPolicy() {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
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
            log.info("Bucket policy set to public");
        } catch (Exception e) {
            log.error("Failed to set bucket policy: {}", e.getMessage());
        }
    }

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

        log.info("=== Starting file upload ===");
        log.info("Original filename: {}", file.getOriginalFilename());
        log.info("Content type: {}", file.getContentType());
        log.info("File size: {} bytes", file.getSize());
        log.info("Object key: {}", objectKey);
        log.info("Bucket: {}", bucket);

        try {
            log.info("Checking bucket existence...");
            ensureBucketExists();
            log.info("Bucket check passed");

            log.info("Uploading to MinIO...");
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());
            log.info("Upload to MinIO successful: {}", objectKey);

        } catch (Exception e) {
            log.error("=== Upload FAILED ===");
            log.error("Bucket: {}", bucket);
            log.error("Object key: {}", objectKey);
            log.error("Exception type: {}", e.getClass().getName());
            log.error("Message: {}", e.getMessage());
            log.error("Full stacktrace:", e);
            throw new RuntimeException("File upload failed: " + e.getMessage());
        }

        String url = publicUrl + "/" + bucket + "/" + objectKey;
        log.info("Public URL: {}", url);

        MediaFile media = MediaFile.builder()
                .uploader(uploader)
                .bucket(bucket)
                .objectKey(objectKey)
                .originalFilename(file.getOriginalFilename())
                .contentType(file.getContentType())
                .sizeBytes(file.getSize())
                .publicUrl(url)
                .entityType(type)
                .entityId(entityId)
                .build();

        MediaFile saved = mediaFileRepository.save(media);
        log.info("MediaFile saved to DB with id: {}", saved.getId());
        return saved;
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

package com.collab.controller;

import com.collab.common.dto.ApiResponse1;
import com.collab.common.enums.MediaFileType;
import com.collab.entity.MediaFile;
import com.collab.entity.User;
import com.collab.security.CurrentUser;
import com.collab.service.MinioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/media")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@SecurityRequirement(name = "Bearer")
@Tag(name = "Media", description = "Загрузка и получение медиафайлов через MinIO")
public class MediaController {

    private final MinioService minioService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
        summary = "Загрузить файл",
        description = """
            Загружает файл в MinIO и возвращает публичный URL.
            
            **Типы и ограничения:**
            
            | type | Допустимые форматы | Макс. размер |
            |------|--------------------|--------------|
            | `USER_AVATAR` | image/* | 10 MB |
            | `TASK_COVER` | image/* | 10 MB |
            | `BRAND_LOGO` | image/* | 10 MB |
            | `PORTFOLIO` | image/*, video/* | 500 MB |
            | `WORK_SUBMISSION` | image/*, video/* | 500 MB |
            | `CHAT_ATTACHMENT` | image/*, video/*, application/pdf | 50 MB |
            
            **Структура ключей в MinIO:**
            - `avatars/{userId}/{uuid}.jpg`
            - `tasks/{taskId}/{uuid}.jpg`
            - `submissions/{applicationId}/{uuid}.mp4`
            - `chat/{applicationId}/{uuid}.pdf`
            """
    )
    public ResponseEntity<ApiResponse1<UploadResponse>> upload(
            @RequestPart("file") MultipartFile file,
            @Parameter(description = "Тип файла: USER_AVATAR | TASK_COVER | PORTFOLIO | WORK_SUBMISSION | CHAT_ATTACHMENT | BRAND_LOGO")
            @RequestParam MediaFileType type,
            @Parameter(description = "UUID сущности (userId, taskId, applicationId...)")
            @RequestParam UUID entityId,
            @CurrentUser User user) {

        validateFile(file, type);
        MediaFile saved = minioService.upload(file, type, entityId, user);

        UploadResponse resp = new UploadResponse();
        resp.setFileId(saved.getId());
        resp.setUrl(saved.getPublicUrl());
        resp.setObjectKey(saved.getObjectKey());
        resp.setContentType(saved.getContentType());
        resp.setSizeBytes(saved.getSizeBytes());

        return ResponseEntity.ok(ApiResponse1.ok("Uploaded successfully", resp));
    }

    @GetMapping("/presigned")
    @Operation(
        summary = "Получить временную ссылку на файл",
        description = """
            Генерирует presigned URL для доступа к приватному файлу.  
            Ссылка действует **1 час**.  
            Используйте для файлов типа `WORK_SUBMISSION` (сданные работы).
            """
    )
    public ResponseEntity<ApiResponse1<String>> presigned(
            @Parameter(description = "Ключ объекта в MinIO (из поля objectKey при загрузке)")
            @RequestParam String objectKey) {
        return ResponseEntity.ok(ApiResponse1.ok(minioService.getPresignedUrl(objectKey)));
    }

    private void validateFile(MultipartFile file, MediaFileType type) {
        if (file.isEmpty()) throw new IllegalArgumentException("File is empty");
        long maxSize = switch (type) {
            case WORK_SUBMISSION, PORTFOLIO -> 500L * 1024 * 1024;
            case CHAT_ATTACHMENT -> 50L * 1024 * 1024;
            default -> 10L * 1024 * 1024;
        };
        if (file.getSize() > maxSize)
            throw new IllegalArgumentException("File too large. Max: " + (maxSize / 1024 / 1024) + "MB");
        String ct = file.getContentType();
        if (ct == null) throw new IllegalArgumentException("Unknown file type");
        boolean valid = switch (type) {
            case USER_AVATAR, TASK_COVER, BRAND_LOGO -> ct.startsWith("image/");
            case PORTFOLIO, WORK_SUBMISSION -> ct.startsWith("image/") || ct.startsWith("video/");
            case CHAT_ATTACHMENT -> ct.startsWith("image/") || ct.startsWith("video/") || ct.equals("application/pdf");
        };
        if (!valid) throw new IllegalArgumentException("File type not allowed for " + type);
    }

    @Data
    static class UploadResponse {
        private UUID fileId;
        private String url;
        private String objectKey;
        private String contentType;
        private Long sizeBytes;
    }
}

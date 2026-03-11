package com.collab.entity;

import com.collab.common.enums.MediaFileType;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "media_files")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@EntityListeners(AuditingEntityListener.class)
public class MediaFile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploader_id", nullable = false)
    private User uploader;

    // MinIO storage info
    @Column(nullable = false)
    private String bucket;

    @Column(name = "object_key", nullable = false)
    private String objectKey;   // e.g. "users/uuid/photo.jpg"

    @Column(name = "original_filename")
    private String originalFilename;

    @Column(name = "content_type")
    private String contentType;  // "image/jpeg", "video/mp4", "application/pdf"

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "public_url")
    private String publicUrl;   // presigned URL или прямая ссылка

    // К чему относится
    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type")
    private MediaFileType entityType;

    @Column(name = "entity_id")
    private UUID entityId;

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}

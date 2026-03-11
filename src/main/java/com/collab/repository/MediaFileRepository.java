package com.collab.repository;

import com.collab.common.enums.MediaFileType;
import com.collab.entity.MediaFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MediaFileRepository extends JpaRepository<MediaFile, UUID> {

    List<MediaFile> findByEntityTypeAndEntityId(MediaFileType entityType, UUID entityId);

    List<MediaFile> findByUploaderIdOrderByCreatedAtDesc(UUID uploaderId);

    void deleteByObjectKey(String objectKey);
}

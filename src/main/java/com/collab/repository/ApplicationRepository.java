package com.collab.repository;

import com.collab.entity.Application;
import com.collab.common.enums.ApplicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApplicationRepository extends JpaRepository<Application, UUID> {

    Page<Application> findByBloggerIdOrderByCreatedAtDesc(UUID bloggerId, Pageable pageable);

    Page<Application> findByTaskIdOrderByCreatedAtDesc(UUID taskId, Pageable pageable);

    List<Application> findByTaskIdAndStatus(UUID taskId, ApplicationStatus status);

    Optional<Application> findByTaskIdAndBloggerId(UUID taskId, UUID bloggerId);

    boolean existsByTaskIdAndBloggerId(UUID taskId, UUID bloggerId);
}

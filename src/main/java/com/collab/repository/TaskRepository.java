package com.collab.repository;

import com.collab.entity.Task;
import com.collab.common.enums.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface TaskRepository extends JpaRepository<Task, UUID>, JpaSpecificationExecutor<Task> {

    Page<Task> findByBrandIdAndStatusNotOrderByCreatedAtDesc(UUID brandId, TaskStatus status, Pageable pageable);

    Page<Task> findByStatusOrderByCreatedAtDesc(TaskStatus status, Pageable pageable);

    Page<Task> findByTitleContainingIgnoreCase(String title, Pageable pageable);

    long countByStatus(TaskStatus status);
}

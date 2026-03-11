package com.collab.repository;

import com.collab.entity.CompletionRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CompletionRecordRepository extends JpaRepository<CompletionRecord, UUID> {

    Optional<CompletionRecord> findByApplicationId(UUID applicationId);

    boolean existsByApplicationId(UUID applicationId);

    Page<CompletionRecord> findByBloggerIdOrderByCompletedAtDesc(UUID bloggerId, Pageable pageable);

    Page<CompletionRecord> findByBrandIdOrderByCompletedAtDesc(UUID brandId, Pageable pageable);

    long countByBloggerId(UUID bloggerId);

    long countByBrandId(UUID brandId);

    @Query("SELECT AVG(c.agreedPrice) FROM CompletionRecord c WHERE c.blogger.id = :bloggerId")
    Double avgPriceForBlogger(@Param("bloggerId") UUID bloggerId);
}

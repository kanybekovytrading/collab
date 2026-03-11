package com.collab.repository;

import com.collab.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review, UUID> {

    Page<Review> findByReviewedIdOrderByCreatedAtDesc(UUID reviewedId, Pageable pageable);

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.reviewed.id = :userId")
    Double calculateAverageRating(@Param("userId") UUID userId);

    long countByReviewedId(UUID reviewedId);

    boolean existsByApplicationIdAndReviewerId(UUID applicationId, UUID reviewerId);
}

package com.collab.repository;

import com.collab.entity.PortfolioItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PortfolioItemRepository extends JpaRepository<PortfolioItem, UUID> {

    List<PortfolioItem> findByBloggerProfileUserIdOrderBySortOrderAsc(UUID userId);

    Optional<PortfolioItem> findByIdAndBloggerProfileUserId(UUID id, UUID userId);

    int countByBloggerProfileUserId(UUID userId);
}
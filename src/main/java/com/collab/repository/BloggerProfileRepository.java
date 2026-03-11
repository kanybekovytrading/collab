package com.collab.repository;

import com.collab.entity.BloggerProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface BloggerProfileRepository extends JpaRepository<BloggerProfile, UUID>,
        JpaSpecificationExecutor<BloggerProfile> {

    Optional<BloggerProfile> findByUserId(UUID userId);

    @Query("""
        SELECT b FROM BloggerProfile b
        WHERE (:country IS NULL OR b.user.country = :country)
          AND (:city    IS NULL OR b.user.city    = :city)
          AND (:minAge  IS NULL OR b.user.age     >= :minAge)
          AND (:maxAge  IS NULL OR b.user.age     <= :maxAge)
        ORDER BY b.rating DESC
    """)
    Page<BloggerProfile> findWithFilters(
            @Param("country") String country,
            @Param("city")    String city,
            @Param("minAge")  Integer minAge,
            @Param("maxAge")  Integer maxAge,
            Pageable pageable
    );
}

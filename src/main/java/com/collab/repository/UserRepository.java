package com.collab.repository;

import com.collab.common.enums.UserRole;
import com.collab.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    Optional<User> findByOauthProviderId(String oauthProviderId);
    boolean existsByEmail(String email);
    boolean existsByPhone(String phone);

    Page<User> findByFullNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
            String name, String email, Pageable pageable);

    @Query("SELECT COUNT(u) FROM User u JOIN u.roles r WHERE r = com.collab.common.enums.UserRole.BLOGGER OR r = com.collab.common.enums.UserRole.AI_CREATOR")
    long countBloggers();

    @Query("SELECT COUNT(u) FROM User u JOIN u.roles r WHERE CAST(r AS string) = :role")
    long countByRole(@Param("role") String role);

    @Query("SELECT DISTINCT u FROM User u JOIN u.roles r WHERE r = :role")
    List<User> findAllByRole(@Param("role") UserRole role);
}

package com.collab.repository;

import com.collab.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    Page<Message> findByApplicationIdOrderByCreatedAtAsc(UUID applicationId, Pageable pageable);

    @Modifying
    @Query("UPDATE Message m SET m.read = true WHERE m.application.id = :appId AND m.sender.id != :userId")
    void markChatAsRead(@Param("appId") UUID appId, @Param("userId") UUID userId);
}

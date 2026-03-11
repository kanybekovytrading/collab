package com.collab.service;

import com.collab.common.dto.PageResponse;
import com.collab.common.enums.NotificationType;
import com.collab.dto.ChatDto;
import com.collab.entity.*;
import com.collab.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final MessageRepository messageRepository;
    private final ApplicationRepository applicationRepository;
    private final NotificationService notificationService;
    private final MinioService minioService;

    @Transactional
    public PageResponse<ChatDto.MessageResponse> getMessages(UUID appId, User user, int page, int size) {
        Application app = getAndCheckAccess(appId, user);
        messageRepository.markChatAsRead(appId, user.getId());
        var pageable = PageRequest.of(page, size, Sort.by("createdAt").ascending());
        return PageResponse.from(messageRepository.findByApplicationIdOrderByCreatedAtAsc(appId, pageable)
                .map(m -> toResponse(m, null)));
    }

    @Transactional
    public ChatDto.MessageResponse send(UUID appId, ChatDto.SendMessageRequest req, User sender) {
        Application app = getAndCheckAccess(appId, sender);

        Message msg = messageRepository.save(Message.builder()
                .application(app)
                .sender(sender)
                .content(req.getContent())
                .attachmentUrl(req.getAttachmentUrl())
                .attachmentType(req.getAttachmentType())
                .build());

        User recipient = app.getBlogger().getId().equals(sender.getId())
                ? app.getTask().getBrand() : app.getBlogger();
        notificationService.send(recipient, NotificationType.NEW_MESSAGE,
                "Новое сообщение", sender.getFullName() + " написал вам",
                appId, "APPLICATION");

        return toResponse(msg, recipient);
    }

    private Application getAndCheckAccess(UUID appId, User user) {
        Application app = applicationRepository.findById(appId)
                .orElseThrow(() -> new RuntimeException("Chat not found"));
        boolean isBlogger = app.getBlogger().getId().equals(user.getId());
        boolean isBrand = app.getTask().getBrand().getId().equals(user.getId());
        if (!isBlogger && !isBrand) throw new AccessDeniedException("No access to this chat");
        return app;
    }

    private ChatDto.MessageResponse toResponse(Message m, User recipient) {
        ChatDto.MessageResponse r = new ChatDto.MessageResponse();
        r.setId(m.getId());
        r.setSenderId(m.getSender().getId());
        r.setSenderName(m.getSender().getFullName());
        r.setSenderAvatar(minioService.resolveUrl(m.getSender().getAvatarUrl()));
        r.setContent(m.getContent());
        r.setAttachmentUrl(minioService.resolveUrl(m.getAttachmentUrl()));
        r.setAttachmentType(m.getAttachmentType());
        r.setRead(m.isRead());
        r.setSystemMessage(m.isSystemMessage());
        if (recipient != null) r.setRecipientId(recipient.getId());
        r.setCreatedAt(m.getCreatedAt());
        return r;
    }
}

package com.collab.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

public class ChatDto {

    @Data
    public static class SendMessageRequest {
        private String content;
        private String attachmentUrl;
        private String attachmentType;
    }

    @Data
    public static class MessageResponse {
        private UUID id;
        private UUID senderId;
        private String senderName;
        private String senderAvatar;
        private String content;
        private String attachmentUrl;
        private String attachmentType;
        private boolean read;
        private boolean systemMessage;
        private java.util.UUID recipientId; // для WS личных сообщений
        private LocalDateTime createdAt;
    }
}

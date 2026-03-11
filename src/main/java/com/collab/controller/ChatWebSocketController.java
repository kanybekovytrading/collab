package com.collab.controller;

import com.collab.dto.ChatDto;
import com.collab.entity.User;
import com.collab.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.UUID;

/**
 * WebSocket контроллер для чата в реальном времени.
 *
 * Клиент подключается через STOMP:
 *   CONNECT  ws://host/ws  (Authorization: Bearer <token>)
 *   SUBSCRIBE /user/queue/messages          — личные входящие
 *   SUBSCRIBE /topic/chat/{applicationId}   — все сообщения чата
 *   SEND /app/chat/{applicationId}          — отправить сообщение
 */
@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/chat/{applicationId}")
    public void sendMessage(
            @DestinationVariable UUID applicationId,
            @Payload ChatDto.SendMessageRequest req,
            Principal principal) {

        User sender = (User) ((org.springframework.security.authentication
                .UsernamePasswordAuthenticationToken) principal).getPrincipal();

        ChatDto.MessageResponse message = chatService.send(applicationId, req, sender);

        // Рассылаем всем подписчикам чата (бренд + блогер)
        messagingTemplate.convertAndSend(
                "/topic/chat/" + applicationId, message);

        // Дополнительно — личное уведомление получателю
        messagingTemplate.convertAndSendToUser(
                message.getRecipientId().toString(),
                "/queue/messages",
                message);
    }

    @MessageMapping("/chat/{applicationId}/typing")
    public void typing(
            @DestinationVariable UUID applicationId,
            Principal principal) {

        User sender = (User) ((org.springframework.security.authentication
                .UsernamePasswordAuthenticationToken) principal).getPrincipal();

        messagingTemplate.convertAndSend(
                "/topic/chat/" + applicationId + "/typing",
                new TypingEvent(sender.getId(), sender.getFullName()));
    }

    public record TypingEvent(java.util.UUID userId, String name) {}
}

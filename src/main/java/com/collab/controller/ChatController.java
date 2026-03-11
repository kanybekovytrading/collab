package com.collab.controller;

import com.collab.common.dto.*;
import com.collab.dto.ChatDto;
import com.collab.entity.User;
import com.collab.security.CurrentUser;
import com.collab.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/chats")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@SecurityRequirement(name = "Bearer")
@Tag(name = "Chat", description = "Чат между блогером и брендом по конкретной заявке. Для real-time используйте WebSocket.")
public class ChatController {

    private final ChatService chatService;

    @GetMapping("/{appId}/messages")
    @Operation(
        summary = "История сообщений чата",
        description = """
            Возвращает сообщения чата по заявке.  
            Доступ только участникам (блогер + бренд).  
            Автоматически помечает входящие сообщения как прочитанные.  
            
            💡 Для real-time сообщений подключитесь через WebSocket:  
            `SUBSCRIBE /topic/chat/{appId}`
            """
    )
    public ResponseEntity<ApiResponse1<PageResponse<ChatDto.MessageResponse>>> getMessages(
            @Parameter(description = "UUID заявки") @PathVariable UUID appId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @CurrentUser User user) {
        return ResponseEntity.ok(ApiResponse1.ok(chatService.getMessages(appId, user, page, size)));
    }

    @PostMapping("/{appId}/messages")
    @Operation(
        summary = "Отправить сообщение (REST)",
        description = """
            Сохраняет сообщение в БД и рассылает через WebSocket подписчикам чата.  
            Можно приложить файл (укажите URL из `/api/v1/media/upload`).  
            Получателю отправляется push-уведомление.
            """
    )
    public ResponseEntity<ApiResponse1<ChatDto.MessageResponse>> send(
            @Parameter(description = "UUID заявки") @PathVariable UUID appId,
            @RequestBody ChatDto.SendMessageRequest req,
            @CurrentUser User user) {
        return ResponseEntity.ok(ApiResponse1.ok(chatService.send(appId, req, user)));
    }
}

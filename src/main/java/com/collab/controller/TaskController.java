package com.collab.controller;

import com.collab.common.dto.*;
import com.collab.dto.TaskDto;
import com.collab.entity.User;
import com.collab.security.CurrentUser;
import com.collab.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
@Tag(name = "Tasks", description = "Создание, просмотр и управление заданиями")
public class TaskController {

    private final TaskService taskService;

    @GetMapping
    @Operation(
        summary = "Список активных заданий",
        description = """
            Публичный эндпоинт. Поддерживает фильтрацию:
            - `taskType` — VIDEO | PHOTO | REELS | AI_PHOTO | AI_TEXT
            - `city` — фильтр по городу
            - `acceptsUgc` — принимает UGC-блогеров
            - `acceptsAi` — принимает AI-креаторов
            - `page`, `size` — пагинация
            """
    )
    public ResponseEntity<ApiResponse1<PageResponse<TaskDto.Response>>> getAll(
            @ModelAttribute TaskDto.FilterRequest f) {
        return ResponseEntity.ok(ApiResponse1.ok(taskService.getAll(f)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Детали задания по ID", description = "Публичный эндпоинт.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Задание найдено"),
        @ApiResponse(responseCode = "404", description = "Задание не найдено")
    })
    public ResponseEntity<ApiResponse1<TaskDto.Response>> getById(
            @Parameter(description = "UUID задания") @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse1.ok(taskService.getById(id)));
    }

    @PostMapping
    @PreAuthorize("hasRole('BRAND')")
    @SecurityRequirement(name = "Bearer")
    @Operation(
        summary = "Создать задание",
        description = "Только для роли `BRAND`. Создаёт новое задание со статусом MODERATION."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Задание создано"),
        @ApiResponse(responseCode = "403", description = "Недостаточно прав (нужна роль BRAND)")
    })
    public ResponseEntity<ApiResponse1<TaskDto.Response>> create(
            @Valid @RequestBody TaskDto.CreateRequest req, @CurrentUser User user) {
        return ResponseEntity.ok(ApiResponse1.ok("Task created", taskService.create(req, user)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "Bearer")
    @Operation(
        summary = "Удалить задание",
        description = """
            Бренд-владелец может удалить своё задание.  
            Администратор может удалить любое задание — при этом бренду придёт уведомление.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Задание удалено"),
        @ApiResponse(responseCode = "403", description = "Нет прав на удаление этого задания"),
        @ApiResponse(responseCode = "404", description = "Задание не найдено")
    })
    public ResponseEntity<ApiResponse1<Void>> delete(
            @Parameter(description = "UUID задания") @PathVariable UUID id,
            @CurrentUser User user) {
        taskService.delete(id, user);
        return ResponseEntity.ok(ApiResponse1.ok("Deleted", null));
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('BRAND')")
    @SecurityRequirement(name = "Bearer")
    @Operation(
        summary = "Мои задания (для бренда)",
        description = "Возвращает все задания текущего бренда, кроме удалённых. С пагинацией."
    )
    public ResponseEntity<ApiResponse1<PageResponse<TaskDto.Response>>> getMy(
            @Parameter(description = "Номер страницы (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Размер страницы") @RequestParam(defaultValue = "20") int size,
            @CurrentUser User user) {
        return ResponseEntity.ok(ApiResponse1.ok(taskService.getMy(user, page, size)));
    }
}

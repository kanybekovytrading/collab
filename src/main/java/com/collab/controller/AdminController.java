package com.collab.controller;

import com.collab.common.dto.*;
import com.collab.common.enums.TaskStatus;
import com.collab.dto.AdminDto;
import com.collab.entity.User;
import com.collab.security.CurrentUser;
import com.collab.service.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "Bearer")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "Панель администратора. Все эндпоинты требуют роль ADMIN.")
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/stats")
    @Operation(
        summary = "Статистика платформы",
        description = "Общие счётчики: пользователи, задания, завершённые сотрудничества."
    )
    public ResponseEntity<ApiResponse1<AdminDto.PlatformStats>> stats() {
        return ResponseEntity.ok(ApiResponse1.ok(adminService.getStats()));
    }

    @GetMapping("/users")
    @Operation(
        summary = "Список пользователей",
        description = "Поиск по имени или email через параметр `search`. Пагинация."
    )
    public ResponseEntity<ApiResponse1<PageResponse<AdminDto.UserAdminView>>> users(
            @Parameter(description = "Поиск по имени или email") @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse1.ok(adminService.getUsers(search, page, size)));
    }

    @PutMapping("/users/{id}/ban")
    @Operation(
        summary = "Заблокировать / разблокировать пользователя",
        description = "Передайте `{\"active\": false}` для блокировки, `{\"active\": true}` для разблокировки."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Статус пользователя обновлён"),
        @ApiResponse(responseCode = "404", description = "Пользователь не найден")
    })
    public ResponseEntity<ApiResponse1<AdminDto.UserAdminView>> banUser(
            @Parameter(description = "UUID пользователя") @PathVariable UUID id,
            @RequestBody AdminDto.BanUserRequest req) {
        return ResponseEntity.ok(ApiResponse1.ok(adminService.setUserActive(id, req.isActive())));
    }

    @PutMapping("/users/{id}/verify")
    @Operation(
        summary = "Верифицировать пользователя",
        description = "Устанавливает галочку верификации. Передайте `{\"verified\": true}` или `false`."
    )
    public ResponseEntity<ApiResponse1<AdminDto.UserAdminView>> verifyUser(
            @Parameter(description = "UUID пользователя") @PathVariable UUID id,
            @RequestBody AdminDto.VerifyUserRequest req) {
        return ResponseEntity.ok(ApiResponse1.ok(adminService.setUserVerified(id, req.isVerified())));
    }

    @PutMapping("/tasks/{id}/verify")
    @Operation(
            summary = "Верифицировать задание",
            description = "Изменяет статус задания после модерации. ACTIVE — публикует задание и уведомляет бренд. DELETED — удаляет задание и уведомляет бренд о нарушении."
    )
    public ResponseEntity<ApiResponse1<AdminDto.TaskAdminView>> verifyTask(
            @Parameter(description = "UUID задания") @PathVariable UUID id,
            @RequestBody TaskStatus request) {
        return ResponseEntity.ok(ApiResponse1.ok(
                adminService.setTaskVerified(id,  request)));
    }

    @GetMapping("/tasks")
    @Operation(
        summary = "Список заданий (для модерации)",
        description = """
            Фильтрация по статусу: `MODERATION | ACTIVE | IN_PROGRESS | COMPLETED | CANCELLED | DELETED`.
            Поиск по заголовку через параметр `search`.
            """
    )
    public ResponseEntity<ApiResponse1<PageResponse<AdminDto.TaskAdminView>>> tasks(
            @Parameter(description = "Статус: ACTIVE | DELETED | COMPLETED ...") @RequestParam(required = false) String status,
            @Parameter(description = "Поиск по заголовку") @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse1.ok(adminService.getTasks(status, search, page, size)));
    }

    @DeleteMapping("/tasks/{id}")
    @Operation(
        summary = "Удалить задание (модерация)",
        description = """
            Задание помечается как `DELETED`.
            Бренду автоматически отправляется уведомление (in-app + push) с указанной причиной.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Задание удалено, бренд уведомлён"),
        @ApiResponse(responseCode = "404", description = "Задание не найдено")
    })
    public ResponseEntity<ApiResponse1<Void>> deleteTask(
            @Parameter(description = "UUID задания") @PathVariable UUID id,
            @RequestBody AdminDto.DeleteTaskRequest req,
            @CurrentUser User admin) {
        adminService.deleteTask(id, req.getReason(), admin);
        return ResponseEntity.ok(ApiResponse1.ok("Task deleted", null));
    }

    @PutMapping("/tasks/{id}/restore")
    @Operation(
        summary = "Восстановить задание",
        description = "Возвращает удалённое задание в статус `ACTIVE`."
    )
    public ResponseEntity<ApiResponse1<AdminDto.TaskAdminView>> restoreTask(
            @Parameter(description = "UUID задания") @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse1.ok(adminService.restoreTask(id)));
    }
}

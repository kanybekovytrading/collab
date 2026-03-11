package com.collab.controller;

import com.collab.common.dto.*;
import com.collab.dto.ApplicationDto;
import com.collab.entity.User;
import com.collab.security.CurrentUser;
import com.collab.service.ApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/applications")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer")
@Tag(name = "Applications", description = "Заявки блогеров на задания. Полный жизненный цикл сотрудничества.")
public class ApplicationController {

    private final ApplicationService applicationService;

    @PostMapping
    @PreAuthorize("hasAnyRole('BLOGGER','AI_CREATOR')")
    @Operation(
        summary = "Подать заявку на задание",
        description = """
            Только для `BLOGGER` и `AI_CREATOR`.  
            Нельзя подать заявку повторно на одно задание.  
            Нельзя подать заявку на неактивное задание.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Заявка подана, бренду отправлено уведомление"),
        @ApiResponse(responseCode = "409", description = "Вы уже подали заявку на это задание"),
        @ApiResponse(responseCode = "400", description = "Задание неактивно")
    })
    public ResponseEntity<ApiResponse1<ApplicationDto.Response>> apply(
            @RequestBody ApplicationDto.ApplyRequest req, @CurrentUser User user) {
        return ResponseEntity.ok(ApiResponse1.ok(
                applicationService.apply(req.getTaskId(), req.getCoverLetter(), req.getProposedPrice(), user)));
    }

    @GetMapping("/my")
    @PreAuthorize("hasAnyRole('BLOGGER','AI_CREATOR')")
    @Operation(summary = "Мои заявки (для блогера)", description = "Все заявки текущего блогера, с пагинацией.")
    public ResponseEntity<ApiResponse1<PageResponse<ApplicationDto.Response>>> getMy(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @CurrentUser User user) {
        return ResponseEntity.ok(ApiResponse1.ok(applicationService.getMy(user, page, size)));
    }

    @GetMapping("/task/{taskId}")
    @PreAuthorize("hasRole('BRAND')")
    @Operation(
        summary = "Заявки на задание (для бренда)",
        description = "Все заявки на конкретное задание. Доступно только владельцу задания."
    )
    public ResponseEntity<ApiResponse1<PageResponse<ApplicationDto.Response>>> getByTask(
            @Parameter(description = "UUID задания") @PathVariable UUID taskId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @CurrentUser User user) {
        return ResponseEntity.ok(ApiResponse1.ok(applicationService.getByTask(taskId, user, page, size)));
    }

    @PutMapping("/{id}/accept")
    @PreAuthorize("hasRole('BRAND')")
    @Operation(
        summary = "Принять заявку",
        description = "Бренд принимает заявку → статус меняется на `IN_WORK`. Блогеру отправляется уведомление."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Заявка принята"),
        @ApiResponse(responseCode = "400", description = "Заявка не в статусе PENDING"),
        @ApiResponse(responseCode = "403", description = "Это не ваше задание")
    })
    public ResponseEntity<ApiResponse1<Void>> accept(
            @Parameter(description = "UUID заявки") @PathVariable UUID id,
            @CurrentUser User user) {
        applicationService.accept(id, user);
        return ResponseEntity.ok(ApiResponse1.ok("Accepted", null));
    }

    @PutMapping("/{id}/reject")
    @PreAuthorize("hasRole('BRAND')")
    @Operation(summary = "Отклонить заявку", description = "Статус → `REJECTED`. Блогеру отправляется уведомление.")
    public ResponseEntity<ApiResponse1<Void>> reject(
            @Parameter(description = "UUID заявки") @PathVariable UUID id,
            @CurrentUser User user) {
        applicationService.reject(id, user);
        return ResponseEntity.ok(ApiResponse1.ok("Rejected", null));
    }

    @PutMapping("/{id}/submit")
    @PreAuthorize("hasAnyRole('BLOGGER','AI_CREATOR')")
    @Operation(
        summary = "Сдать работу",
        description = """
            Блогер сдаёт выполненную работу. Доступно из статусов `IN_WORK` и `REVISION_REQUESTED`.  
            Статус → `SUBMITTED`. Бренду отправляется уведомление о проверке.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Работа сдана"),
        @ApiResponse(responseCode = "400", description = "Неверный текущий статус"),
        @ApiResponse(responseCode = "403", description = "Это не ваша заявка")
    })
    public ResponseEntity<ApiResponse1<Void>> submit(
            @Parameter(description = "UUID заявки") @PathVariable UUID id,
            @RequestBody ApplicationDto.SubmitRequest req,
            @CurrentUser User user) {
        applicationService.submitWork(id, req.getWorkUrl(), req.getComment(), user);
        return ResponseEntity.ok(ApiResponse1.ok("Work submitted", null));
    }

    @PutMapping("/{id}/revision")
    @PreAuthorize("hasRole('BRAND')")
    @Operation(
        summary = "Запросить доработку",
        description = """
            Бренд отклоняет сданную работу и просит переделать.  
            Статус → `REVISION_REQUESTED`. Счётчик `revisionCount` увеличивается.  
            Блогеру отправляется уведомление с комментарием.
            """
    )
    public ResponseEntity<ApiResponse1<Void>> requestRevision(
            @Parameter(description = "UUID заявки") @PathVariable UUID id,
            @RequestBody RevisionRequest req,
            @CurrentUser User user) {
        applicationService.requestRevision(id, req.getComment(), user);
        return ResponseEntity.ok(ApiResponse1.ok("Revision requested", null));
    }

    @PutMapping("/{id}/approve")
    @PreAuthorize("hasRole('BRAND')")
    @Operation(
        summary = "Принять финальную работу → завершить сотрудничество",
        description = """
            Бренд принимает работу.  
            Статус → `COMPLETED`.  
            Автоматически:
            - создаётся `CompletionRecord` (фиксация факта завершения)
            - обновляется счётчик выполненных заданий у блогера
            - обновляется счётчик кампаний у бренда
            - разрешается оставить отзыв обеим сторонам
            - блогеру отправляется уведомление
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Сотрудничество завершено"),
        @ApiResponse(responseCode = "400", description = "Работа ещё не сдана (нужен статус SUBMITTED)")
    })
    public ResponseEntity<ApiResponse1<Void>> approve(
            @Parameter(description = "UUID заявки") @PathVariable UUID id,
            @CurrentUser User user) {
        applicationService.approve(id, user);
        return ResponseEntity.ok(ApiResponse1.ok("Approved and completed", null));
    }

    @PutMapping("/{id}/cancel")
    @PreAuthorize("isAuthenticated()")
    @Operation(
        summary = "Отменить заявку",
        description = "Блогер или бренд могут отменить заявку. Нельзя отменить завершённую (`COMPLETED`)."
    )
    public ResponseEntity<ApiResponse1<Void>> cancel(
            @Parameter(description = "UUID заявки") @PathVariable UUID id,
            @CurrentUser User user) {
        applicationService.cancel(id, user);
        return ResponseEntity.ok(ApiResponse1.ok("Cancelled", null));
    }

    @PostMapping("/invite")
    @PreAuthorize("hasRole('BRAND')")
    @Operation(
        summary = "Пригласить блогера на задание",
        description = """
            Бренд может лично пригласить конкретного блогера на своё задание.  
            Создаётся заявка с флагом `invited=true`.  
            Блогеру отправляется push-уведомление и email.
            """
    )
    public ResponseEntity<ApiResponse1<Void>> invite(
            @RequestBody ApplicationDto.InviteRequest req, @CurrentUser User user) {
        applicationService.invite(req.getTaskId(), req.getBloggerId(), user);
        return ResponseEntity.ok(ApiResponse1.ok("Invited", null));
    }

    @Data
    static class RevisionRequest {
        private String comment;
    }
}

package com.collab.controller;

import com.collab.common.dto.*;
import com.collab.dto.ReviewDto;
import com.collab.entity.User;
import com.collab.security.CurrentUser;
import com.collab.service.ReviewService;
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
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
@Tag(name = "Reviews", description = "Отзывы о блогерах и брендах после завершения сотрудничества")
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "Bearer")
    @Operation(
        summary = "Оставить отзыв",
        description = """
            Строгие правила допуска:
            1. Только участник заявки (блогер или бренд)
            2. Только если статус заявки — `COMPLETED`
            3. Только если существует `CompletionRecord`
            4. Только один раз на одну заявку
            
            Бренд оставляет отзыв о блогере, блогер — о бренде.  
            После сохранения автоматически пересчитывается рейтинг получателя.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Отзыв сохранён, рейтинг обновлён"),
        @ApiResponse(responseCode = "403", description = "Вы не участник этого сотрудничества"),
        @ApiResponse(responseCode = "409", description = "Вы уже оставили отзыв"),
        @ApiResponse(responseCode = "400", description = "Сотрудничество ещё не завершено")
    })
    public ResponseEntity<ApiResponse1<ReviewDto.Response>> create(
            @Valid @RequestBody ReviewDto.CreateRequest req, @CurrentUser User user) {
        return ResponseEntity.ok(ApiResponse1.ok(reviewService.create(req, user)));
    }

    @GetMapping("/user/{userId}")
    @Operation(
        summary = "Отзывы о пользователе",
        description = "Публичный эндпоинт. Возвращает все отзывы о конкретном блогере или бренде."
    )
    public ResponseEntity<ApiResponse1<PageResponse<ReviewDto.Response>>> getByUser(
            @Parameter(description = "UUID пользователя") @PathVariable UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse1.ok(reviewService.getByUser(userId, page, size)));
    }

    @GetMapping("/can-review/{applicationId}")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "Bearer")
    @Operation(
        summary = "Проверить, можно ли оставить отзыв",
        description = """
            Вызывайте перед показом кнопки «Оставить отзыв» в UI.  
            Возвращает `canReview: true/false` и причину отказа если нельзя.
            """
    )
    public ResponseEntity<ApiResponse1<ReviewDto.CanReviewResponse>> canReview(
            @Parameter(description = "UUID заявки") @PathVariable UUID applicationId,
            @CurrentUser User user) {
        return ResponseEntity.ok(ApiResponse1.ok(reviewService.canReview(applicationId, user)));
    }
}

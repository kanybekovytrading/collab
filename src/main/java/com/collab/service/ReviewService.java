package com.collab.service;

import com.collab.common.dto.PageResponse;
import com.collab.common.enums.ApplicationStatus;
import com.collab.common.enums.NotificationType;
import com.collab.dto.ReviewDto;
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
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ApplicationRepository applicationRepository;
    private final CompletionRecordRepository completionRecordRepository;
    private final BloggerProfileRepository bloggerProfileRepository;
    private final BrandProfileRepository brandProfileRepository;
    private final NotificationService notificationService;

    @Transactional
    public ReviewDto.Response create(ReviewDto.CreateRequest req, User reviewer) {
        Application app = applicationRepository.findById(req.getApplicationId())
                .orElseThrow(() -> new RuntimeException("Application not found"));

        // ✅ ПРОВЕРКА 1: Только участники могут оставлять отзыв
        boolean isBlogger = app.getBlogger().getId().equals(reviewer.getId());
        boolean isBrand   = app.getTask().getBrand().getId().equals(reviewer.getId());
        if (!isBlogger && !isBrand) {
            throw new AccessDeniedException("Только участники сотрудничества могут оставлять отзыв");
        }

        // ✅ ПРОВЕРКА 2: Сотрудничество должно быть завершено (статус COMPLETED)
        if (app.getStatus() != ApplicationStatus.COMPLETED) {
            throw new IllegalStateException("Отзыв можно оставить только после завершения сотрудничества");
        }

        // ✅ ПРОВЕРКА 3: Должна существовать запись о завершении (CompletionRecord)
        if (!completionRecordRepository.existsByApplicationId(req.getApplicationId())) {
            throw new IllegalStateException("Запись о завершении не найдена");
        }

        // ✅ ПРОВЕРКА 4: Каждый участник может оставить только один отзыв
        if (reviewRepository.existsByApplicationIdAndReviewerId(req.getApplicationId(), reviewer.getId())) {
            throw new IllegalStateException("Вы уже оставили отзыв по этому сотрудничеству");
        }

        // Определяем о ком отзыв: бренд пишет о блогере, блогер — о бренде
        User reviewed = isBrand ? app.getBlogger() : app.getTask().getBrand();

        Review review = reviewRepository.save(Review.builder()
                .application(app)
                .reviewer(reviewer)
                .reviewed(reviewed)
                .rating(req.getRating())
                .comment(req.getComment())
                .taskTitle(app.getTask().getTitle())
                .taskTypeLabel(app.getTask().getTaskType() != null ? app.getTask().getTaskType().name() : null)
                .build());

        // Пересчитываем рейтинг получателя отзыва
        recalculateRating(reviewed, isBrand);

        notificationService.send(reviewed, NotificationType.REVIEW_RECEIVED,
                "Новый отзыв ⭐", reviewer.getFullName() + " оставил вам отзыв — " + req.getRating() + "/5",
                review.getId(), "REVIEW");

        return toResponse(review);
    }

    @Transactional(readOnly = true)
    public PageResponse<ReviewDto.Response> getByUser(UUID userId, int page, int size) {
        return PageResponse.from(reviewRepository
                .findByReviewedIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size, Sort.by("createdAt").descending()))
                .map(this::toResponse));
    }

    // Проверяет, может ли текущий пользователь оставить отзыв
    @Transactional(readOnly = true)
    public ReviewDto.CanReviewResponse canReview(UUID applicationId, User user) {
        Application app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new RuntimeException("Application not found"));

        boolean isParticipant = app.getBlogger().getId().equals(user.getId())
                || app.getTask().getBrand().getId().equals(user.getId());
        boolean isCompleted   = app.getStatus() == ApplicationStatus.COMPLETED;
        boolean alreadyReviewed = reviewRepository.existsByApplicationIdAndReviewerId(applicationId, user.getId());

        ReviewDto.CanReviewResponse resp = new ReviewDto.CanReviewResponse();
        resp.setCanReview(isParticipant && isCompleted && !alreadyReviewed);
        resp.setReason(!isParticipant  ? "Вы не участник этого сотрудничества"
                : !isCompleted         ? "Сотрудничество ещё не завершено"
                : alreadyReviewed      ? "Вы уже оставили отзыв"
                :                        null);
        return resp;
    }

    private void recalculateRating(User reviewed, boolean reviewedIsBlogger) {
        Double avg = reviewRepository.calculateAverageRating(reviewed.getId());
        long count = reviewRepository.countByReviewedId(reviewed.getId());
        double newRating = avg != null ? avg : 5.0;

        if (reviewedIsBlogger) {
            bloggerProfileRepository.findByUserId(reviewed.getId()).ifPresent(p -> {
                p.setRating(newRating);
                p.setReviewsCount((int) count);
                bloggerProfileRepository.save(p);
            });
        } else {
            brandProfileRepository.findByUserId(reviewed.getId()).ifPresent(p -> {
                p.setRating(newRating);
                p.setReviewsCount((int) count);
                brandProfileRepository.save(p);
            });
        }
    }

    private ReviewDto.Response toResponse(Review r) {
        ReviewDto.Response dto = new ReviewDto.Response();
        dto.setId(r.getId());
        dto.setReviewerId(r.getReviewer().getId());
        dto.setReviewerName(r.getReviewer().getFullName());
        dto.setReviewerAvatar(r.getReviewer().getAvatarUrl());
        dto.setRating(r.getRating());
        dto.setComment(r.getComment());
        dto.setTaskTitle(r.getTaskTitle());
        dto.setTaskTypeLabel(r.getTaskTypeLabel());
        dto.setCreatedAt(r.getCreatedAt());
        return dto;
    }
}

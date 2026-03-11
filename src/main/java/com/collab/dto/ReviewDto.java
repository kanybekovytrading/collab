package com.collab.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

public class ReviewDto {

    @Data
    public static class CreateRequest {
        @NotNull private UUID applicationId;
        @NotNull @Min(1) @Max(5) private Integer rating;
        private String comment;
    }

    @Data
    public static class Response {
        private UUID id;
        private UUID reviewerId;
        private String reviewerName;
        private String reviewerAvatar;
        private Integer rating;
        private String comment;
        private String taskTitle;
        private String taskTypeLabel;
        private LocalDateTime createdAt;
    }

    @Data
    public static class CanReviewResponse {
        private boolean canReview;
        private String reason;
    }
}

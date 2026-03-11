package com.collab.dto;

import com.collab.common.enums.ApplicationStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public class ApplicationDto {

    @Data
    public static class ApplyRequest {
        private UUID taskId;
        private String coverLetter;
        private BigDecimal proposedPrice;
    }

    @Data
    public static class SubmitRequest {
        private String workUrl;
        private String comment;
    }

    @Data
    public static class InviteRequest {
        private UUID taskId;
        private UUID bloggerId;
    }

    @Data
    public static class Response {
        private UUID id;
        private UUID taskId;
        private String taskTitle;
        private UUID bloggerId;
        private String bloggerName;
        private ApplicationStatus status;
        private BigDecimal proposedPrice;
        private String submittedWorkUrl;
        private boolean invited;
        private LocalDateTime createdAt;
    }
}

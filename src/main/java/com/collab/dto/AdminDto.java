package com.collab.dto;

import com.collab.common.enums.TaskStatus;
import com.collab.common.enums.UserRole;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

public class AdminDto {

    // ---- Статистика платформы ----
    @Data
    public static class PlatformStats {
        private long totalUsers;
        private long totalBloggers;
        private long totalBrands;
        private long totalTasks;
        private long activeTasks;
        private long completedCollaborations;
        private long totalApplications;
        private LocalDateTime generatedAt;
    }

    // ---- Пользователь для модерации ----
    @Data
    public static class UserAdminView {
        private UUID id;
        private String fullName;
        private String email;
        private String phone;
        private Set<UserRole> roles;
        private String currentRole;
        private boolean verified;
        private boolean active;
        private String city;
        private String country;
        private LocalDateTime createdAt;
    }

    // ---- Задание для модерации ----
    @Data
    public static class TaskAdminView {
        private UUID id;
        private String title;
        private String description;
        private String taskType;
        private TaskStatus status;
        private String brandName;
        private String brandEmail;
        private LocalDateTime createdAt;
    }

    // ---- Действия администратора ----
    @Data
    public static class BanUserRequest {
        private boolean active; // false = заблокировать
        private String reason;
    }

    @Data
    public static class VerifyUserRequest {
        private boolean verified;
    }

    @Data
    public static class DeleteTaskRequest {
        private String reason;
    }
}

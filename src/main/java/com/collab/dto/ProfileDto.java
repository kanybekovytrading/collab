package com.collab.dto;

import com.collab.common.enums.ContentCategory;
import com.collab.common.enums.TaskType;
import com.collab.common.enums.UserRole;
import com.collab.entity.SocialAccount;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class ProfileDto {

    @Data
    public static class UpdateBloggerRequest {
        private String bio;
        private Set<ContentCategory> categories;
        private BigDecimal minPrice;
        private boolean worksWithBarter;
        private List<SocialRequest> socialAccounts;
    }

    @Data
    public static class UpdateBrandRequest {
        private String companyName;
        private String description;
        private String websiteUrl;
        private ContentCategory category;
        private List<SocialRequest> socialAccounts;
    }

    @Data
    public static class SocialRequest {
        private SocialAccount.Platform platform;
        private String username;
        private String url;
        private Long followersCount;
    }

    @Data
    public static class BloggerResponse {
        private UUID id;
        private String fullName;
        private String avatarUrl;
        private String city;
        private String country;
        private Integer age;
        private boolean verified;
        private String bio;
        private Set<ContentCategory> categories;
        private BigDecimal minPrice;
        private boolean worksWithBarter;
        private Double rating;
        private Integer completedTasksCount;
        private Integer reviewsCount;
        private List<SocialInfo> socialAccounts;
        private List<PortfolioItemResponse> portfolioItems;
        private int rank;
    }

    @Data
    public static class BrandResponse {
        private UUID id;
        private String fullName;
        private String companyName;
        private String avatarUrl;
        private String city;
        private boolean verified;
        private String description;
        private String websiteUrl;
        private ContentCategory category;
        private Double rating;
        private Integer tasksCount;
        private Integer reviewsCount;
        private List<SocialInfo> socialAccounts;
    }

    @Data
    public static class SocialInfo {
        private SocialAccount.Platform platform;
        private String username;
        private String url;
        private Long followersCount;
    }

    @Data
    public static class PortfolioItemRequest {
        @NotBlank private String mediaUrl;
        @Size(max = 200) private String title;
        private TaskType contentType;
        private String thumbnailUrl;
        private Integer sortOrder;
    }

    @Data
    public static class PortfolioItemResponse {
        private UUID id;
        private String mediaUrl;
        private String title;
        private TaskType contentType;
        private String thumbnailUrl;
        private Integer sortOrder;
    }

    @Data
    public static class ReorderRequest {
        /** Список ID в нужном порядке — индекс = новый sortOrder */
        private List<UUID> orderedIds;
    }

    @Data
    public static class AddRoleRequest {
        @NotNull private UserRole role;
    }

    @Data
    public static class SwitchRoleRequest {
        @NotNull
        private UserRole role;
    }

    @Data
    public static class RolesResponse {
        private String currentRole;
        private java.util.Set<String> allRoles;
    }
}

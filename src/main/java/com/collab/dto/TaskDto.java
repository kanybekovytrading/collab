package com.collab.dto;

import com.collab.common.enums.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

public class TaskDto {

    @Data
    public static class CreateRequest {
        @NotBlank private String title;
        private String description;
        private TaskType taskType;
        private String coverImageUrl;
        private String city;
        private boolean online;
        private Integer deadlineDays;
        private BigDecimal price;
        private String priceDescription;
        private boolean acceptsUgc = true;
        private boolean acceptsAi = false;
        private Set<Gender> genderFilter;
        private Set<ContentCategory> categories;
    }

    @Data
    public static class FilterRequest {
        private TaskType taskType;
        private String city;
        private Boolean acceptsUgc;
        private Boolean acceptsAi;
        private int page = 0;
        private int size = 20;
    }

    @Data
    public static class Response {
        private UUID id;
        private String title;
        private String description;
        private TaskType taskType;
        private String coverImageUrl;
        private String city;
        private boolean online;
        private Integer deadlineDays;
        private BigDecimal price;
        private String priceDescription;
        private TaskStatus status;
        private int reactionsCount;
        private boolean acceptsUgc;
        private boolean acceptsAi;
        private Set<Gender> genderFilter;
        private Set<ContentCategory> categories;
        private LocalDateTime createdAt;
        private BrandInfo brand;
    }

    @Data
    public static class BrandInfo {
        private UUID id;
        private String fullName;
        private String companyName;
        private String avatarUrl;
        private boolean verified;
        private Double rating;
    }
}

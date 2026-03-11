package com.collab.entity;

import com.collab.common.enums.*;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "tasks")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Task extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id", nullable = false)
    private User brand;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type")
    private TaskType taskType;

    @Column(name = "cover_image_url")
    private String coverImageUrl;

    private String city;

    @Column(name = "is_online")
    @Builder.Default
    private boolean online = false;

    @Column(name = "deadline_days")
    private Integer deadlineDays;

    private BigDecimal price;

    @Column(name = "price_description")
    private String priceDescription;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private TaskStatus status = TaskStatus.MODERATION;

    @Column(name = "reactions_count")
    @Builder.Default
    private Integer reactionsCount = 0;

    @Column(name = "accepts_ugc")
    @Builder.Default
    private boolean acceptsUgc = true;

    @Column(name = "accepts_ai")
    @Builder.Default
    private boolean acceptsAi = false;

    @ElementCollection
    @CollectionTable(name = "task_gender_filter", joinColumns = @JoinColumn(name = "task_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "gender")
    @Builder.Default
    private Set<Gender> genderFilter = new HashSet<>();

    @ElementCollection
    @CollectionTable(name = "task_categories", joinColumns = @JoinColumn(name = "task_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "category")
    @Builder.Default
    private Set<ContentCategory> categories = new HashSet<>();
}

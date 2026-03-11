package com.collab.entity;

import com.collab.common.enums.TaskType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "portfolio_items")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PortfolioItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "blogger_profile_id", nullable = false)
    private BloggerProfile bloggerProfile;

    private String title;

    @Column(name = "media_url", nullable = false)
    private String mediaUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_type")
    private TaskType contentType;

    @Column(name = "thumbnail_url")
    private String thumbnailUrl;

    @Column(name = "sort_order")
    @Builder.Default
    private Integer sortOrder = 0;
}

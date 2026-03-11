package com.collab.entity;

import com.collab.common.enums.ContentCategory;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.*;

@Entity
@Table(name = "blogger_profiles")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BloggerProfile extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true, nullable = false)
    private User user;

    @Column(length = 300)
    private String bio;

    @ElementCollection
    @CollectionTable(name = "blogger_categories", joinColumns = @JoinColumn(name = "blogger_profile_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "category")
    @Builder.Default
    private Set<ContentCategory> categories = new HashSet<>();

    @Column(name = "min_price")
    private BigDecimal minPrice;

    @Column(name = "works_with_barter")
    @Builder.Default
    private boolean worksWithBarter = false;

    @Builder.Default
    private Double rating = 5.0;

    @Column(name = "completed_tasks_count")
    @Builder.Default
    private Integer completedTasksCount = 0;

    @Column(name = "reviews_count")
    @Builder.Default
    private Integer reviewsCount = 0;

    @OneToMany(mappedBy = "bloggerProfile", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<SocialAccount> socialAccounts = new ArrayList<>();

    @OneToMany(mappedBy = "bloggerProfile", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PortfolioItem> portfolioItems = new ArrayList<>();
}

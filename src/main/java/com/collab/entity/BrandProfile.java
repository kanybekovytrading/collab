package com.collab.entity;

import com.collab.common.enums.ContentCategory;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "brand_profiles")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BrandProfile extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true, nullable = false)
    private User user;

    @Column(name = "company_name")
    private String companyName;

    @Column(length = 1000)
    private String description;

    @Column(name = "website_url")
    private String websiteUrl;

    @Enumerated(EnumType.STRING)
    private ContentCategory category;

    @Builder.Default
    private Double rating = 5.0;

    @Column(name = "tasks_count")
    @Builder.Default
    private Integer tasksCount = 0;

    @Column(name = "reviews_count")
    @Builder.Default
    private Integer reviewsCount = 0;

    @OneToMany(mappedBy = "brandProfile", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<SocialAccount> socialAccounts = new ArrayList<>();
}

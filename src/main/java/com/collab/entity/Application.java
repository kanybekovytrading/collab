package com.collab.entity;

import com.collab.common.enums.ApplicationStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "applications",
    uniqueConstraints = @UniqueConstraint(columnNames = {"task_id", "blogger_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Application extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "blogger_id", nullable = false)
    private User blogger;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ApplicationStatus status = ApplicationStatus.PENDING;

    @Column(name = "cover_letter", length = 1000)
    private String coverLetter;

    @Column(name = "proposed_price")
    private BigDecimal proposedPrice;

    @Column(name = "submitted_work_url")
    private String submittedWorkUrl;

    @Column(name = "submitted_work_comment", length = 500)
    private String submittedWorkComment;

    @Column(name = "is_invited")
    @Builder.Default
    private boolean invited = false;

    // Доработки
    @Column(name = "revision_count")
    @Builder.Default
    private Integer revisionCount = 0;

    @Column(name = "revision_comment", length = 500)
    private String revisionComment;

    // Финальные данные при завершении
    @Column(name = "completed_at")
    private java.time.LocalDateTime completedAt;

    @Column(name = "final_price")
    private java.math.BigDecimal finalPrice;
}

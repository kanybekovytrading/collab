package com.collab.entity;

import com.collab.common.enums.TaskType;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Фиксирует факт завершённого сотрудничества.
 * Создаётся автоматически когда бренд нажимает "Принять работу" (COMPLETED).
 * Используется для:
 * - статистики блогера (сколько заданий выполнил)
 * - статистики бренда (сколько кампаний провёл)
 * - разрешения оставить отзыв
 * - аналитики платформы
 */
@Entity
@Table(name = "completion_records")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@EntityListeners(AuditingEntityListener.class)
public class CompletionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", unique = true, nullable = false)
    private Application application;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "blogger_id", nullable = false)
    private User blogger;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id", nullable = false)
    private User brand;

    // Финансовая сторона
    @Column(name = "agreed_price")
    private BigDecimal agreedPrice;

    @Column(name = "currency")
    @Builder.Default
    private String currency = "KGS";

    // Что было сделано
    @Enumerated(EnumType.STRING)
    @Column(name = "task_type")
    private TaskType taskType;

    @Column(name = "task_title")
    private String taskTitle;

    // Кто и когда подтвердил
    @Column(name = "completed_at", nullable = false)
    private LocalDateTime completedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "completed_by_brand_id", nullable = false)
    private User completedByBrand;

    // Сколько итераций потребовалось
    @Column(name = "revision_count")
    @Builder.Default
    private Integer revisionCount = 0;

    // Финальные файлы
    @Column(name = "final_work_url")
    private String finalWorkUrl;

    @Column(name = "final_work_comment", columnDefinition = "TEXT")
    private String finalWorkComment;

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}

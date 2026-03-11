package com.collab.service;

import com.collab.common.dto.PageResponse;
import com.collab.common.enums.*;
import com.collab.dto.ApplicationDto;
import com.collab.entity.*;
import com.collab.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final CompletionRecordRepository completionRecordRepository;
    private final BloggerProfileRepository bloggerProfileRepository;
    private final BrandProfileRepository brandProfileRepository;
    private final NotificationService notificationService;

    @Transactional
    public ApplicationDto.Response apply(UUID taskId, String coverLetter, BigDecimal proposedPrice, User blogger) {
        Task task = taskRepository.findById(taskId).orElseThrow(() -> new RuntimeException("Task not found"));
        if (task.getStatus() != TaskStatus.ACTIVE) throw new IllegalStateException("Task is not active");
        if (applicationRepository.existsByTaskIdAndBloggerId(taskId, blogger.getId()))
            throw new IllegalStateException("Already applied");

        Application app = applicationRepository.save(Application.builder()
                .task(task).blogger(blogger).coverLetter(coverLetter).proposedPrice(proposedPrice).build());

        notificationService.send(task.getBrand(), NotificationType.APPLICATION_RECEIVED,
                "Новая заявка", blogger.getFullName() + " откликнулся на «" + task.getTitle() + "»",
                app.getId(), "APPLICATION");
        return toResponse(app);
    }

    @Transactional
    public void accept(UUID appId, User brand) {
        Application app = getAndCheckBrand(appId, brand);
        if (app.getStatus() != ApplicationStatus.PENDING) throw new IllegalStateException("Not pending");
        app.setStatus(ApplicationStatus.IN_WORK);
        applicationRepository.save(app);
        notificationService.send(app.getBlogger(), NotificationType.APPLICATION_ACCEPTED,
                "Заявка принята!", "Бренд принял вашу заявку. Напишите им!", appId, "APPLICATION");
    }

    @Transactional
    public void reject(UUID appId, User brand) {
        Application app = getAndCheckBrand(appId, brand);
        if (app.getStatus() != ApplicationStatus.PENDING) throw new IllegalStateException("Not pending");
        app.setStatus(ApplicationStatus.REJECTED);
        applicationRepository.save(app);
        notificationService.send(app.getBlogger(), NotificationType.APPLICATION_REJECTED,
                "Заявка отклонена", "К сожалению, бренд не выбрал вас", appId, "APPLICATION");
    }

    // Блогер сдаёт работу — работает и из IN_WORK и после REVISION_REQUESTED
    @Transactional
    public void submitWork(UUID appId, String workUrl, String comment, User blogger) {
        Application app = applicationRepository.findById(appId)
                .orElseThrow(() -> new RuntimeException("Application not found"));
        if (!app.getBlogger().getId().equals(blogger.getId())) throw new AccessDeniedException("Not yours");
        if (app.getStatus() != ApplicationStatus.IN_WORK && app.getStatus() != ApplicationStatus.REVISION_REQUESTED)
            throw new IllegalStateException("Cannot submit in current status: " + app.getStatus());

        app.setStatus(ApplicationStatus.SUBMITTED);
        app.setSubmittedWorkUrl(workUrl);
        app.setSubmittedWorkComment(comment);
        applicationRepository.save(app);

        notificationService.send(app.getTask().getBrand(), NotificationType.WORK_SUBMITTED,
                "Работа сдана", app.getBlogger().getFullName() + " сдал работу", appId, "APPLICATION");
    }

    // Бренд просит доработать
    @Transactional
    public void requestRevision(UUID appId, String comment, User brand) {
        Application app = getAndCheckBrand(appId, brand);
        if (app.getStatus() != ApplicationStatus.SUBMITTED)
            throw new IllegalStateException("Work must be submitted first");

        app.setStatus(ApplicationStatus.REVISION_REQUESTED);
        app.setRevisionComment(comment);
        app.setRevisionCount(app.getRevisionCount() + 1);
        applicationRepository.save(app);

        notificationService.send(app.getBlogger(), NotificationType.REVISION_REQUESTED,
                "Нужна доработка", "Бренд просит доработать: " + (comment != null ? comment : "уточнения в чате"),
                appId, "APPLICATION");
    }

    // Бренд принимает работу → создаём CompletionRecord
    @Transactional
    public void approve(UUID appId, User brand) {
        Application app = getAndCheckBrand(appId, brand);
        if (app.getStatus() != ApplicationStatus.SUBMITTED)
            throw new IllegalStateException("Work must be submitted to approve");

        LocalDateTime now = LocalDateTime.now();
        BigDecimal finalPrice = app.getProposedPrice() != null ? app.getProposedPrice() : app.getTask().getPrice();

        app.setStatus(ApplicationStatus.COMPLETED);
        app.setCompletedAt(now);
        app.setFinalPrice(finalPrice);
        applicationRepository.save(app);

        // ✅ Фиксируем завершение — разрешает оставлять отзывы
        completionRecordRepository.save(CompletionRecord.builder()
                .application(app)
                .task(app.getTask())
                .blogger(app.getBlogger())
                .brand(brand)
                .agreedPrice(finalPrice)
                .taskType(app.getTask().getTaskType())
                .taskTitle(app.getTask().getTitle())
                .completedAt(now)
                .completedByBrand(brand)
                .revisionCount(app.getRevisionCount())
                .finalWorkUrl(app.getSubmittedWorkUrl())
                .finalWorkComment(app.getSubmittedWorkComment())
                .build());

        // Обновляем счётчик выполненных у блогера
        bloggerProfileRepository.findByUserId(app.getBlogger().getId()).ifPresent(p -> {
            p.setCompletedTasksCount(p.getCompletedTasksCount() + 1);
            bloggerProfileRepository.save(p);
        });

        // Обновляем счётчик кампаний у бренда
        brandProfileRepository.findByUserId(brand.getId()).ifPresent(p -> {
            p.setTasksCount(p.getTasksCount() + 1);
            brandProfileRepository.save(p);
        });

        notificationService.send(app.getBlogger(), NotificationType.WORK_APPROVED,
                "Работа принята! 🎉", "Теперь можно оставить отзыв о бренде", appId, "APPLICATION");
    }

    @Transactional
    public void invite(UUID taskId, UUID bloggerId, User brand) {
        Task task = taskRepository.findById(taskId).orElseThrow(() -> new RuntimeException("Task not found"));
        if (!task.getBrand().getId().equals(brand.getId())) throw new AccessDeniedException("Not your task");
        if (applicationRepository.existsByTaskIdAndBloggerId(taskId, bloggerId))
            throw new IllegalStateException("Already applied or invited");

        User blogger = userRepository.findById(bloggerId).orElseThrow(() -> new RuntimeException("Blogger not found"));
        Application app = applicationRepository.save(Application.builder().task(task).blogger(blogger).invited(true).build());

        notificationService.send(blogger, NotificationType.BLOGGER_INVITED,
                "Вас пригласили!", "Бренд приглашает вас на «" + task.getTitle() + "»", app.getId(), "APPLICATION");
    }

    @Transactional
    public void cancel(UUID appId, User requester) {
        Application app = applicationRepository.findById(appId)
                .orElseThrow(() -> new RuntimeException("Application not found"));
        boolean isBlogger = app.getBlogger().getId().equals(requester.getId());
        boolean isBrand = app.getTask().getBrand().getId().equals(requester.getId());
        if (!isBlogger && !isBrand) throw new AccessDeniedException("Not a participant");
        if (app.getStatus() == ApplicationStatus.COMPLETED) throw new IllegalStateException("Already completed");
        app.setStatus(ApplicationStatus.CANCELLED);
        applicationRepository.save(app);
    }

    @Transactional(readOnly = true)
    public PageResponse<ApplicationDto.Response> getMy(User blogger, int page, int size) {
        return PageResponse.from(applicationRepository
                .findByBloggerIdOrderByCreatedAtDesc(blogger.getId(), PageRequest.of(page, size, Sort.by("createdAt").descending()))
                .map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public PageResponse<ApplicationDto.Response> getByTask(UUID taskId, User brand, int page, int size) {
        Task task = taskRepository.findById(taskId).orElseThrow(() -> new RuntimeException("Task not found"));
        if (!task.getBrand().getId().equals(brand.getId())) throw new AccessDeniedException("Not your task");
        return PageResponse.from(applicationRepository
                .findByTaskIdOrderByCreatedAtDesc(taskId, PageRequest.of(page, size, Sort.by("createdAt").descending()))
                .map(this::toResponse));
    }

    private Application getAndCheckBrand(UUID appId, User brand) {
        Application app = applicationRepository.findById(appId)
                .orElseThrow(() -> new RuntimeException("Application not found"));
        if (!app.getTask().getBrand().getId().equals(brand.getId())) throw new AccessDeniedException("Not your task");
        return app;
    }

    private ApplicationDto.Response toResponse(Application a) {
        ApplicationDto.Response r = new ApplicationDto.Response();
        r.setId(a.getId());
        r.setTaskId(a.getTask().getId());
        r.setTaskTitle(a.getTask().getTitle());
        r.setBloggerId(a.getBlogger().getId());
        r.setBloggerName(a.getBlogger().getFullName());
        r.setStatus(a.getStatus());
        r.setProposedPrice(a.getProposedPrice());
        r.setSubmittedWorkUrl(a.getSubmittedWorkUrl());
        r.setInvited(a.isInvited());
        r.setCreatedAt(a.getCreatedAt());
        return r;
    }
}

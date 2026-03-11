package com.collab.service;

import com.collab.common.dto.PageResponse;
import com.collab.common.enums.*;
import com.collab.dto.TaskDto;
import com.collab.entity.*;
import com.collab.repository.TaskRepository;
import com.collab.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final MinioService minioService;

    @Transactional
    public TaskDto.Response create(TaskDto.CreateRequest req, User brand) {
        Task task = Task.builder()
                .brand(brand)
                .title(req.getTitle())
                .description(req.getDescription())
                .taskType(req.getTaskType())
                .coverImageUrl(req.getCoverImageUrl())
                .city(req.getCity())
                .online(req.isOnline())
                .deadlineDays(req.getDeadlineDays())
                .price(req.getPrice())
                .priceDescription(req.getPriceDescription())
                .acceptsUgc(req.isAcceptsUgc())
                .acceptsAi(req.isAcceptsAi())
                .genderFilter(req.getGenderFilter() != null ? req.getGenderFilter() : new java.util.HashSet<>())
                .categories(req.getCategories() != null ? req.getCategories() : new java.util.HashSet<>())
                .build();
        Task saved = taskRepository.save(task);

        // Уведомляем всех админов
        userRepository.findAllByRole(UserRole.ADMIN).forEach(admin ->
                notificationService.send(
                        admin,
                        NotificationType.TASK_PENDING_REVIEW,
                        "Новое задание на модерацию",
                        "Бренд «" + brand.getFullName() + "» создал задание «" + saved.getTitle() + "»",
                        saved.getId(), "TASK"
                )
        );

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<TaskDto.Response> getAll(TaskDto.FilterRequest f) {
        Specification<Task> spec = (root, q, cb) -> cb.equal(root.get("status"), TaskStatus.ACTIVE);
        if (f.getTaskType() != null)
            spec = spec.and((r, q, cb) -> cb.equal(r.get("taskType"), f.getTaskType()));
        if (f.getCity() != null)
            spec = spec.and((r, q, cb) -> cb.equal(r.get("city"), f.getCity()));
        if (Boolean.TRUE.equals(f.getAcceptsUgc()))
            spec = spec.and((r, q, cb) -> cb.isTrue(r.get("acceptsUgc")));
        if (Boolean.TRUE.equals(f.getAcceptsAi()))
            spec = spec.and((r, q, cb) -> cb.isTrue(r.get("acceptsAi")));

        var pageable = PageRequest.of(f.getPage(), f.getSize(), Sort.by("createdAt").descending());
        return PageResponse.from(taskRepository.findAll(spec, pageable).map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public TaskDto.Response getById(UUID id) {
        return toResponse(taskRepository.findById(id).orElseThrow(() -> new RuntimeException("Task not found")));
    }

    @Transactional
    public void delete(UUID id, User requester) {
        Task task = taskRepository.findById(id).orElseThrow(() -> new RuntimeException("Task not found"));
        boolean isOwner = task.getBrand().getId().equals(requester.getId());
        boolean isAdmin = requester.getRoles().contains(UserRole.ADMIN);
        if (!isOwner && !isAdmin) throw new AccessDeniedException("Not authorized");

        task.setStatus(TaskStatus.DELETED);
        taskRepository.save(task);

        if (isAdmin && !isOwner) {
            notificationService.send(task.getBrand(), NotificationType.TASK_DELETED_BY_ADMIN,
                    "Ваше объявление удалено",
                    "Администратор удалил Ваше объявление, т.к. оно не соответствует правилам платформы",
                    id, "TASK");
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<TaskDto.Response> getMy(User brand, int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return PageResponse.from(
                taskRepository.findByBrandIdAndStatusNotOrderByCreatedAtDesc(brand.getId(), TaskStatus.DELETED, pageable)
                        .map(this::toResponse));
    }

    private TaskDto.Response toResponse(Task t) {
        TaskDto.Response r = new TaskDto.Response();
        r.setId(t.getId());
        r.setTitle(t.getTitle());
        r.setDescription(t.getDescription());
        r.setTaskType(t.getTaskType());
        r.setCoverImageUrl(minioService.resolveUrl(t.getCoverImageUrl()));
        r.setCity(t.getCity());
        r.setOnline(t.isOnline());
        r.setDeadlineDays(t.getDeadlineDays());
        r.setPrice(t.getPrice());
        r.setPriceDescription(t.getPriceDescription());
        r.setStatus(t.getStatus());
        r.setReactionsCount(t.getReactionsCount());
        r.setAcceptsUgc(t.isAcceptsUgc());
        r.setAcceptsAi(t.isAcceptsAi());
        r.setGenderFilter(t.getGenderFilter());
        r.setCategories(t.getCategories());
        r.setCreatedAt(t.getCreatedAt());

        TaskDto.BrandInfo b = new TaskDto.BrandInfo();
        b.setId(t.getBrand().getId());
        b.setFullName(t.getBrand().getFullName());
        b.setAvatarUrl(minioService.resolveUrl(t.getBrand().getAvatarUrl()));
        b.setVerified(t.getBrand().isVerified());
        if (t.getBrand().getBrandProfile() != null) {
            b.setCompanyName(t.getBrand().getBrandProfile().getCompanyName());
            b.setRating(t.getBrand().getBrandProfile().getRating());
        }
        r.setBrand(b);
        return r;
    }
}

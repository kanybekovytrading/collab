package com.collab.service;

import com.collab.common.dto.PageResponse;
import com.collab.common.enums.NotificationType;
import com.collab.common.enums.TaskStatus;
import com.collab.dto.AdminDto;
import com.collab.entity.Task;
import com.collab.entity.User;
import com.collab.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final TaskRepository taskRepository;
    private final ApplicationRepository applicationRepository;
    private final CompletionRecordRepository completionRecordRepository;
    private final NotificationService notificationService;

    // ---- Статистика ----

    @Transactional(readOnly = true)
    public AdminDto.PlatformStats getStats() {
        AdminDto.PlatformStats stats = new AdminDto.PlatformStats();
        stats.setTotalUsers(userRepository.count());
        stats.setTotalBloggers(userRepository.countByRole("BLOGGER"));
        stats.setTotalBrands(userRepository.countByRole("BRAND"));
        stats.setTotalTasks(taskRepository.count());
        stats.setActiveTasks(taskRepository.countByStatus(TaskStatus.ACTIVE));
        stats.setCompletedCollaborations(completionRecordRepository.count());
        stats.setTotalApplications(applicationRepository.count());
        stats.setGeneratedAt(LocalDateTime.now());
        return stats;
    }

    // ---- Пользователи ----

    @Transactional(readOnly = true)
    public PageResponse<AdminDto.UserAdminView> getUsers(String search, int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        var pg = (search != null && !search.isBlank())
                ? userRepository.findByFullNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                        search, search, pageable)
                : userRepository.findAll(pageable);
        return PageResponse.from(pg.map(this::toUserView));
    }

    @Transactional
    public AdminDto.UserAdminView setUserActive(UUID userId, boolean active) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setActive(active);
        return toUserView(userRepository.save(user));
    }

    @Transactional
    public AdminDto.UserAdminView setUserVerified(UUID userId, boolean verified) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setVerified(verified);
        return toUserView(userRepository.save(user));
    }



    // ---- Задания ----

    @Transactional
    public AdminDto.TaskAdminView setTaskVerified(UUID taskId, TaskStatus taskStatus) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found"));
        if(taskStatus.equals(TaskStatus.DELETED)) {
            task.setStatus(taskStatus);
               deleteTask(taskId, null, null);

        } else if (taskStatus.equals(TaskStatus.ACTIVE)) {
            task.setStatus(taskStatus);
            notificationService.send(
                    task.getBrand(),
                    NotificationType.TASK_VERIFIED,
                    "Объявление опубликовано",
                    "Ваше задание «" + task.getTitle() + "» прошло модерацию и опубликовано",
                    taskId, "TASK");
        }
        return toTaskView(taskRepository.save(task));
    }


    @Transactional(readOnly = true)
    public PageResponse<AdminDto.TaskAdminView> getTasks(String status, String search, int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        TaskStatus taskStatus = null;
        try { if (status != null) taskStatus = TaskStatus.valueOf(status.toUpperCase()); }
        catch (IllegalArgumentException ignored) {}

        var pg = (search != null && !search.isBlank())
                ? taskRepository.findByTitleContainingIgnoreCase(search, pageable)
                : (taskStatus != null
                    ? taskRepository.findByStatusOrderByCreatedAtDesc(taskStatus, pageable)
                    : taskRepository.findAll(pageable));

        return PageResponse.from(pg.map(this::toTaskView));
    }

    @Transactional
    public void deleteTask(UUID taskId, String reason, User admin) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found"));

        task.setStatus(TaskStatus.DELETED);
        taskRepository.save(task);

        notificationService.send(
                task.getBrand(),
                NotificationType.TASK_DELETED_BY_ADMIN,
                "Ваше объявление удалено",
                "Администратор удалил задание «" + task.getTitle() + "»" +
                        (reason != null ? ": " + reason : " (нарушение правил платформы)"),
                taskId, "TASK");
    }

    @Transactional
    public AdminDto.TaskAdminView restoreTask(UUID taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found"));
        task.setStatus(TaskStatus.ACTIVE);
        return toTaskView(taskRepository.save(task));
    }

    // ---- Mappers ----

    private AdminDto.UserAdminView toUserView(User u) {
        AdminDto.UserAdminView v = new AdminDto.UserAdminView();
        v.setId(u.getId());
        v.setFullName(u.getFullName());
        v.setEmail(u.getEmail());
        v.setPhone(u.getPhone());
        v.setRoles(u.getRoles());
        v.setCurrentRole(u.getCurrentRole().name());
        v.setVerified(u.isVerified());
        v.setActive(u.isActive());
        v.setCity(u.getCity());
        v.setCountry(u.getCountry());
        v.setCreatedAt(u.getCreatedAt());
        return v;
    }

    private AdminDto.TaskAdminView toTaskView(Task t) {
        AdminDto.TaskAdminView v = new AdminDto.TaskAdminView();
        v.setId(t.getId());
        v.setTitle(t.getTitle());
        v.setDescription(t.getDescription());
        v.setTaskType(t.getTaskType() != null ? t.getTaskType().name() : null);
        v.setStatus(t.getStatus());
        v.setBrandName(t.getBrand().getFullName());
        v.setBrandEmail(t.getBrand().getEmail());
        v.setCreatedAt(t.getCreatedAt());
        return v;
    }
}

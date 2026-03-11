package com.collab.service;

import com.collab.common.enums.NotificationType;
import com.collab.entity.Notification;
import com.collab.entity.User;
import com.collab.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final FcmService fcmService;

    @Transactional
    public void send(User recipient, NotificationType type,
                     String title, String body,
                     UUID referenceId, String referenceType) {

        // 1. Сохраняем в БД
        Notification notification = Notification.builder()
                .recipient(recipient)
                .type(type)
                .title(title)
                .body(body)
                .referenceId(referenceId)
                .referenceType(referenceType)
                .build();
        notificationRepository.save(notification);

        // 2. Отправляем FCM push (если есть токен)
        if (recipient.getFcmToken() != null) {
            fcmService.sendToDevice(
                    recipient.getFcmToken(),
                    title,
                    body,
                    referenceId != null ? referenceId.toString() : null,
                    referenceType
            );
        }
    }
}
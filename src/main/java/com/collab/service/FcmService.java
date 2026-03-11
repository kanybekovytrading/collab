package com.collab.service;

import com.google.firebase.messaging.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class FcmService {

    /**
     * Отправляет push-уведомление на конкретное устройство по FCM token.
     */
    public void sendToDevice(String fcmToken, String title, String body, String referenceId, String referenceType) {
        if (fcmToken == null || fcmToken.isBlank()) return;

        try {
            Message message = Message.builder()
                    .setToken(fcmToken)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .putData("referenceId", referenceId != null ? referenceId : "")
                    .putData("referenceType", referenceType != null ? referenceType : "")
                    .setAndroidConfig(AndroidConfig.builder()
                            .setPriority(AndroidConfig.Priority.HIGH)
                            .build())
                    .setApnsConfig(ApnsConfig.builder()
                            .setAps(Aps.builder()
                                    .setSound("default")
                                    .setBadge(1)
                                    .build())
                            .build())
                    .build();

            String response = FirebaseMessaging.getInstance().send(message);
            log.debug("FCM sent: {}", response);

        } catch (FirebaseMessagingException e) {
            log.warn("FCM send failed for token {}: {}", fcmToken.substring(0, 10) + "...", e.getMessage());
        }
    }

    /**
     * Отправляет push нескольким устройствам (multicast).
     */
    public void sendToDevices(List<String> tokens, String title, String body) {
        if (tokens == null || tokens.isEmpty()) return;

        try {
            MulticastMessage message = MulticastMessage.builder()
                    .addAllTokens(tokens)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .build();

            BatchResponse response = FirebaseMessaging.getInstance().sendEachForMulticast(message);
            log.debug("FCM multicast: {}/{} успешно", response.getSuccessCount(), tokens.size());

        } catch (FirebaseMessagingException e) {
            log.warn("FCM multicast failed: {}", e.getMessage());
        }
    }
}

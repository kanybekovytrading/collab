package com.collab.controller;

import com.collab.common.dto.ApiResponse1;
import com.collab.entity.User;
import com.collab.repository.UserRepository;
import com.collab.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/device")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@SecurityRequirement(name = "Bearer")
@Tag(name = "Device", description = "Регистрация устройства для push-уведомлений")
public class DeviceController {

    private final UserRepository userRepository;

    @PutMapping("/fcm-token")
    @Operation(
        summary = "Обновить FCM token устройства",
        description = """
            Вызывайте при каждом старте приложения, если Firebase вернул новый токен.  
            Токен используется для отправки push-уведомлений через Firebase Cloud Messaging.  
            При смене устройства старый токен автоматически перезаписывается.
            """
    )
    public ResponseEntity<ApiResponse1<Void>> updateFcmToken(
            @RequestBody FcmTokenRequest req, @CurrentUser User user) {
        user.setFcmToken(req.getToken());
        userRepository.save(user);
        return ResponseEntity.ok(ApiResponse1.ok("Token updated", null));
    }

    @Data
    static class FcmTokenRequest {
        private String token;
    }
}

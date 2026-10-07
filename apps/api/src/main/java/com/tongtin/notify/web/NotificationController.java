package com.tongtin.notify.web;

import com.tongtin.common.security.AuthPrincipal;
import com.tongtin.notify.dto.NotificationListResponse;
import com.tongtin.notify.dto.NotificationResponse;
import com.tongtin.notify.service.NotificationService;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public NotificationListResponse list(@AuthenticationPrincipal AuthPrincipal principal) {
        return notificationService.list(principal.userId());
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(@AuthenticationPrincipal AuthPrincipal principal) {
        return Map.of("unreadCount", notificationService.unreadCount(principal.userId()));
    }

    @PostMapping("/{id}/read")
    public NotificationResponse markRead(@AuthenticationPrincipal AuthPrincipal principal,
                                         @PathVariable Long id) {
        return notificationService.markRead(principal.userId(), id);
    }

    @PostMapping("/read-all")
    public Map<String, Integer> markAllRead(@AuthenticationPrincipal AuthPrincipal principal) {
        return Map.of("updatedCount", notificationService.markAllRead(principal.userId()));
    }
}
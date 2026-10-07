package com.tongtin.notify.dto;

import com.tongtin.notify.entity.Notification;
import java.time.Instant;

public record NotificationResponse(
        Long id,
        String type,
        String title,
        String body,
        Instant readAt,
        Instant createdAt) {

    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(
                n.getId(), n.getType(), n.getTitle(), n.getBody(), n.getReadAt(), n.getCreatedAt());
    }
}
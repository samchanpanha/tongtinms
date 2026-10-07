package com.tongtin.notify.dto;

import java.util.List;

public record NotificationListResponse(List<NotificationResponse> notifications, long unreadCount) {
}
package com.tongtin.telegram.dto;

/** Step 35 host Telegram status = POST/PUT result shape. */
public record TelegramStatusResponse(
        Long chatId,
        boolean linked,
        boolean eventsEnabled,
        boolean digestEnabled,
        String digestTime,
        boolean eventsConfigured) {
}
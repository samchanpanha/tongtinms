package com.tongtin.telegram.dto;

/** Step 35 host request to set (positive long) or clear (null) their chat id. */
public record SetChatIdRequest(Long chatId) {
}
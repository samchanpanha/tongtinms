package com.tongtin.telegram.web;

import com.tongtin.common.security.AuthPrincipal;
import com.tongtin.telegram.TelegramService;
import com.tongtin.telegram.dto.SetChatIdRequest;
import com.tongtin.telegram.dto.TelegramStatusResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Step 35: host Telegram linking + test-send (host-only, own account only). */
@RestController
@RequestMapping("/api/v1/host/telegram")
@PreAuthorize("hasRole('HOST')")
public class TelegramController {

    private final TelegramService telegramService;

    public TelegramController(TelegramService telegramService) {
        this.telegramService = telegramService;
    }

    @GetMapping
    public TelegramStatusResponse status(@AuthenticationPrincipal AuthPrincipal principal) {
        return telegramService.status(principal.ownerId());
    }

    @PutMapping("/chat-id")
    public TelegramStatusResponse setChatId(@AuthenticationPrincipal AuthPrincipal principal,
                                            @RequestBody(required = false) SetChatIdRequest request) {
        Long chatId = request == null ? null : request.chatId();
        return telegramService.setChatId(principal.userId(), principal.ownerId(), chatId);
    }

    @PostMapping("/test")
    public TelegramStatusResponse testSend(@AuthenticationPrincipal AuthPrincipal principal) {
        return telegramService.testSend(principal.userId(), principal.ownerId());
    }
}
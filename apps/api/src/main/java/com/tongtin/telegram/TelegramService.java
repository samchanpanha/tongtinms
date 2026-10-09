package com.tongtin.telegram;

import com.tongtin.common.errors.BadRequestException;
import com.tongtin.common.errors.NotFoundException;
import com.tongtin.identity.entity.OwnerAccount;
import com.tongtin.identity.repository.OwnerAccountRepository;
import com.tongtin.identity.service.AuditService;
import com.tongtin.settings.service.SettingsService;
import com.tongtin.telegram.dto.TelegramStatusResponse;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Step 35: host-scoped Telegram link management (own chat id only) and
 * test-send. Scope is enforced by caller-ownerId: responses only ever touch
 * {@code ownerId}'s own row (cross-owner handled at the controller by passing
 * the authenticated ownerId). Audit rows for link/unlink/test are written in
 * the same transaction as the chat-id change.
 */
@Service
public class TelegramService {

    private static final Logger log = LoggerFactory.getLogger(TelegramService.class);

    private final OwnerAccountRepository ownerAccountRepository;
    private final SettingsService settingsService;
    private final TelegramClient client;
    private final AuditService auditService;

    public TelegramService(OwnerAccountRepository ownerAccountRepository,
                           SettingsService settingsService,
                           TelegramClient client,
                           AuditService auditService) {
        this.ownerAccountRepository = ownerAccountRepository;
        this.settingsService = settingsService;
        this.client = client;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public TelegramStatusResponse status(Long ownerId) {
        return buildStatus(findOwner(ownerId));
    }

    @Transactional
    public TelegramStatusResponse setChatId(Long userId, Long ownerId, Long chatId) {
        OwnerAccount owner = findOwner(ownerId);
        if (chatId == null) {
            if (owner.getTelegramChatId() != null) {
                owner.setTelegramChatId(null);
                ownerAccountRepository.save(owner);
                auditService.record(userId, "OwnerAccount", owner.getId(), "TELEGRAM_UNLINKED", Map.of());
            }
            return buildStatus(owner);
        }
        if (chatId <= 0) {
            throw new BadRequestException("chatId must be a positive integer");
        }
        owner.setTelegramChatId(chatId);
        ownerAccountRepository.save(owner);
        auditService.record(userId, "OwnerAccount", owner.getId(), "TELEGRAM_LINKED",
                Map.of("chatId", chatId));
        return buildStatus(owner);
    }

    @Transactional
    public TelegramStatusResponse testSend(Long userId, Long ownerId) {
        OwnerAccount owner = findOwner(ownerId);
        if (owner.getTelegramChatId() == null) {
            throw new BadRequestException("No Telegram chat id linked");
        }
        String token = settingsService.getString("telegram_bot_token", "");
        if (token.isBlank()) {
            throw new BadRequestException("Telegram bot not configured (admin must set telegram_bot_token)");
        }
        String text = TelegramMessages.testPing(owner.getDisplayName());
        boolean ok = client.sendMessage(token, owner.getTelegramChatId(), text);
        auditService.record(userId, "OwnerAccount", owner.getId(), "TELEGRAM_TEST",
                Map.of("chatId", owner.getTelegramChatId(), "ok", ok));
        if (!ok) {
            log.warn("Telegram test-send failed for owner {} (chat {})", ownerId, owner.getTelegramChatId());
            throw new BadRequestException("Telegram send failed: check bot token and chat id");
        }
        return buildStatus(owner);
    }

    private OwnerAccount findOwner(Long ownerId) {
        return ownerAccountRepository.findById(ownerId)
                .orElseThrow(() -> new NotFoundException("Owner not found"));
    }

    private TelegramStatusResponse buildStatus(OwnerAccount owner) {
        String token = settingsService.getString("telegram_bot_token", "");
        return new TelegramStatusResponse(
                owner.getTelegramChatId(),
                owner.getTelegramChatId() != null,
                settingsService.getBoolean("telegram_events_enabled", true),
                settingsService.getBoolean("telegram_daily_digest_enabled", false),
                settingsService.getString("telegram_daily_digest_time", "08:00"),
                token != null && !token.isBlank());
    }
}
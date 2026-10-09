package com.tongtin.telegram;

import com.tongtin.groups.entity.Group;
import com.tongtin.identity.entity.OwnerAccount;
import com.tongtin.identity.repository.OwnerAccountRepository;
import com.tongtin.settings.service.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Step 35: best-effort delivery of Telegram pings to a group's host.
 *
 * Mirrors {@code NotificationService} invariants: a Telegram send can NEVER
 * throw into or roll back a financial operation — the whole method is guarded.
 * Sends are synchronous side effects (no scheduler for money; Step 14's "sync
 * transitions only" decision stands). Skipped (silently, no network) when the
 * channel is off: events disabled, no linked chat id, or no bot token.
 */
@Service
public class TelegramNotifier {

    private static final Logger log = LoggerFactory.getLogger(TelegramNotifier.class);

    private final TelegramClient client;
    private final SettingsService settingsService;
    private final OwnerAccountRepository ownerAccountRepository;

    public TelegramNotifier(TelegramClient client,
                            SettingsService settingsService,
                            OwnerAccountRepository ownerAccountRepository) {
        this.client = client;
        this.settingsService = settingsService;
        this.ownerAccountRepository = ownerAccountRepository;
    }

    /** Sends a best-effort event ping to the host who owns {@code group}. */
    public void notifyGroupOwner(Group group, String type, String text) {
        notifyOwnerId(group.getOwnerId(), type, text);
    }

    /** Sends a best-effort ping to the owner of the given owner account id. */
    public void notifyOwnerId(Long ownerId, String type, String text) {
        try {
            if (!settingsService.getBoolean("telegram_events_enabled", true)) {
                return;
            }
            String token = settingsService.getString("telegram_bot_token", "");
            if (token.isBlank()) {
                return;
            }
            OwnerAccount owner = ownerAccountRepository.findById(ownerId).orElse(null);
            if (owner == null || owner.getTelegramChatId() == null) {
                return;
            }
            boolean ok = client.sendMessage(token, owner.getTelegramChatId(), text);
            if (!ok) {
                log.warn("Telegram {} failed for owner {} (chat {})", type, ownerId, owner.getTelegramChatId());
            }
        } catch (RuntimeException ex) {
            log.warn("Telegram {} skipped for owner {}: {}", type, ownerId, ex.getMessage());
        }
    }
}
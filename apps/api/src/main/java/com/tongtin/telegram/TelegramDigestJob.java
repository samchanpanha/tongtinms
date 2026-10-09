package com.tongtin.telegram;

import com.tongtin.groups.entity.Group;
import com.tongtin.groups.repository.GroupRepository;
import com.tongtin.identity.entity.OwnerAccount;
import com.tongtin.identity.repository.OwnerAccountRepository;
import com.tongtin.ledger.entity.LedgerEntry;
import com.tongtin.ledger.payments.repository.PaymentAllocationRepository;
import com.tongtin.ledger.repository.LedgerEntryRepository;
import com.tongtin.settings.service.SettingsService;
import com.tongtin.telegram.TelegramMessages.GroupSummary;
import java.time.Instant;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Step 35: opt-in daily due-digest for hosts with a linked Telegram chat.
 *
 * Heuristics vs pure scheduling: the configured {@code HH:mm} (setting
 * {@code telegram_daily_digest_time}) can change at runtime, so the job runs
 * every minute and only fires inside {@code process(Instant)} when the wall
 * clock minute equals the configured time. Money is never triggered by this
 * job — it only *announces* owed totals (01-DOMAIN §17).
 */
@Component
public class TelegramDigestJob {

    private static final Logger log = LoggerFactory.getLogger(TelegramDigestJob.class);

    private final OwnerAccountRepository ownerAccountRepository;
    private final GroupRepository groupRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final PaymentAllocationRepository allocationRepository;
    private final SettingsService settingsService;
    private final TelegramClient client;

    public TelegramDigestJob(OwnerAccountRepository ownerAccountRepository,
                             GroupRepository groupRepository,
                             LedgerEntryRepository ledgerEntryRepository,
                             PaymentAllocationRepository allocationRepository,
                             SettingsService settingsService,
                             TelegramClient client) {
        this.ownerAccountRepository = ownerAccountRepository;
        this.groupRepository = groupRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.allocationRepository = allocationRepository;
        this.settingsService = settingsService;
        this.client = client;
    }

    public record Result(int messagedOwners, int groupsWithDebt) {
    }

    @Scheduled(cron = "0 * * * * *")
    public void runEveryMinute() {
        try {
            if (!settingsService.getBoolean("telegram_daily_digest_enabled", false)) {
                return;
            }
            LocalTime target = parseTime(settingsService.getString("telegram_daily_digest_time", "08:00"));
            if (!LocalTime.now().truncatedTo(ChronoUnit.MINUTES).equals(target)) {
                return;
            }
            Result result = process(Instant.now());
            log.info("Telegram digest run: {} owner(s) messaged across {} group(s)",
                    result.messagedOwners(), result.groupsWithDebt());
        } catch (RuntimeException ex) {
            log.error("Telegram digest job failed: {}", ex.getMessage(), ex);
        }
    }

    /** Pure-ish digest dispatch, directly testable with an arbitrary {@code now}. */
    public Result process(Instant now) {
        if (!settingsService.getBoolean("telegram_daily_digest_enabled", false)) {
            return new Result(0, 0);
        }
        String token = settingsService.getString("telegram_bot_token", "");
        if (token.isBlank()) {
            return new Result(0, 0);
        }

        int messaged = 0;
        int groupsWithDebt = 0;
        for (OwnerAccount owner : ownerAccountRepository.findByTelegramChatIdIsNotNull()) {
            List<GroupSummary> summaries = summarize(owner.getId(), now);
            if (summaries.isEmpty()) {
                continue;
            }
            boolean ok = client.sendMessage(token, owner.getTelegramChatId(),
                    TelegramMessages.dailyDigest(summaries));
            if (ok) {
                messaged++;
                groupsWithDebt += summaries.size();
            }
        }
        return new Result(messaged, groupsWithDebt);
    }

    private List<GroupSummary> summarize(Long ownerId, Instant now) {
        List<GroupSummary> out = new ArrayList<>();
        for (Group group : groupRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId)) {
            List<LedgerEntry> overdue = ledgerEntryRepository.findOverdueByGroup(group.getId(), now).stream()
                    .filter(entry -> "IN".equals(entry.getDirection()))
                    .toList();
            if (overdue.isEmpty()) {
                continue;
            }
            Map<Long, Long> allocated = allocations(overdue);
            long totalRemaining = 0;
            for (LedgerEntry entry : overdue) {
                totalRemaining += Math.max(0, entry.getAmountMinor()
                        - allocated.getOrDefault(entry.getId(), 0L));
            }
            if (totalRemaining > 0) {
                out.add(new GroupSummary(group.getName(), group.getCode(),
                        overdue.size(), totalRemaining, group.getCurrency()));
            }
        }
        return out;
    }

    private Map<Long, Long> allocations(List<LedgerEntry> entries) {
        List<Long> ids = entries.stream().map(LedgerEntry::getId).toList();
        return allocationRepository.sumAllocatedByEntryIds(ids).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1],
                        (a, b) -> a, HashMap::new));
    }

    private static LocalTime parseTime(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (!trimmed.matches("\\d{1,2}:\\d{2}")) {
            return LocalTime.of(8, 0);
        }
        String padded = trimmed.length() == 4 ? "0" + trimmed : trimmed;
        try {
            return LocalTime.parse(padded);
        } catch (RuntimeException ex) {
            return LocalTime.of(8, 0);
        }
    }
}
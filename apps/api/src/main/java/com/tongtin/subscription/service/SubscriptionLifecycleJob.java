package com.tongtin.subscription.service;

import com.tongtin.identity.entity.OwnerAccount;
import com.tongtin.identity.repository.OwnerAccountRepository;
import com.tongtin.notify.service.NotificationService;
import com.tongtin.settings.service.SettingsService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Daily subscription lifecycle: expiry reminders on the configured reminder days
 * (default 7/3/1) and automatic expiry once an owner is past the configured grace
 * period. Skipped entirely when subscription enforcement is switched off.
 */
@Component
public class SubscriptionLifecycleJob {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionLifecycleJob.class);
    private static final List<Integer> DEFAULT_REMINDER_DAYS = List.of(7, 3, 1);
    private static final List<String> SKIP_STATUSES = List.of("EXPIRED", "LIFETIME");

    private final OwnerAccountRepository ownerAccountRepository;
    private final SubscriptionGuard subscriptionGuard;
    private final SettingsService settingsService;
    private final NotificationService notificationService;

    public SubscriptionLifecycleJob(
            OwnerAccountRepository ownerAccountRepository,
            SubscriptionGuard subscriptionGuard,
            SettingsService settingsService,
            NotificationService notificationService) {
        this.ownerAccountRepository = ownerAccountRepository;
        this.subscriptionGuard = subscriptionGuard;
        this.settingsService = settingsService;
        this.notificationService = notificationService;
    }

    public record Result(int remindersSent, int expiredCount) {
    }

    @Scheduled(cron = "0 0 8 * * *")
    public void runDaily() {
        try {
            Result result = process(Instant.now());
            log.info("Subscription lifecycle run: {} reminder(s), {} expired",
                    result.remindersSent(), result.expiredCount());
        } catch (RuntimeException ex) {
            log.error("Subscription lifecycle job failed: {}", ex.getMessage(), ex);
        }
    }

    @Transactional
    public Result process(Instant now) {
        if (!subscriptionGuard.isSubscriptionEnabled() || !subscriptionGuard.isEnforcementEnabled()) {
            return new Result(0, 0);
        }

        int graceDays = settingsService.getInt("grace_period_days", 3);
        List<Integer> reminderDayList = settingsService.getIntList("subscription_reminder_days", DEFAULT_REMINDER_DAYS);
        Set<Long> reminderDays = reminderDayList.stream()
                .map(Integer::longValue)
                .collect(Collectors.toUnmodifiableSet());
        long scanHorizonDays = reminderDayList.stream().mapToLong(Integer::longValue).max().orElse(7L) + 1;

        // One bounded scan covers both reminders (endsAt within the reminder horizon) and expired owners (endsAt in the past).
        List<OwnerAccount> candidates = ownerAccountRepository
                .findBySubscriptionEndsAtBeforeAndSubscriptionStatusNotIn(
                        now.plus(Duration.ofDays(scanHorizonDays)), SKIP_STATUSES);

        int remindersSent = 0;
        int expiredCount = 0;
        for (OwnerAccount owner : candidates) {
            Instant endsAt = owner.getSubscriptionEndsAt();
            if (endsAt == null) {
                continue;
            }

            if (now.isBefore(endsAt)) {
                long daysRemaining = Duration.between(now, endsAt).toDays();
                if (reminderDays.contains(daysRemaining)) {
                    notificationService.notifyUser(owner.getUserId(), "SUBSCRIPTION_EXPIRING",
                            "Gói hụi sắp hết hạn",
                            "Gói sử dụng của bạn sẽ hết hạn sau " + daysRemaining
                                    + " ngày. Gia hạn để tiếp tục tạo dây hụi mới.");
                    remindersSent++;
                }
            } else if (now.isAfter(endsAt.plus(Duration.ofDays(graceDays)))) {
                owner.setSubscriptionStatus("EXPIRED");
                ownerAccountRepository.save(owner);
                notificationService.notifyUser(owner.getUserId(), "SUBSCRIPTION_EXPIRED",
                        "Gói hụi đã hết hạn",
                        "Gói sử dụng của bạn đã hết hạn và quá thời gian gia hạn. "
                                + "Vui lòng gia hạn gói để tiếp tục tạo dây hụi mới.");
                expiredCount++;
            }
        }
        return new Result(remindersSent, expiredCount);
    }
}

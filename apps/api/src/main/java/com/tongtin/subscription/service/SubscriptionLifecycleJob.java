package com.tongtin.subscription.service;

import com.tongtin.identity.entity.OwnerAccount;
import com.tongtin.identity.repository.OwnerAccountRepository;
import com.tongtin.notify.service.NotificationService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Daily subscription lifecycle: expiry reminders at 7/3/1 days remaining and
 * automatic expiry once an owner is past the configured grace period.
 * Skipped entirely when subscription enforcement is switched off.
 */
@Component
public class SubscriptionLifecycleJob {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionLifecycleJob.class);
    private static final Set<Long> REMINDER_DAYS = Set.of(7L, 3L, 1L);
    private static final List<String> SKIP_STATUSES = List.of("EXPIRED", "LIFETIME");

    private final OwnerAccountRepository ownerAccountRepository;
    private final SubscriptionGuard subscriptionGuard;
    private final PayWayService payWayService;
    private final NotificationService notificationService;

    public SubscriptionLifecycleJob(
            OwnerAccountRepository ownerAccountRepository,
            SubscriptionGuard subscriptionGuard,
            PayWayService payWayService,
            NotificationService notificationService) {
        this.ownerAccountRepository = ownerAccountRepository;
        this.subscriptionGuard = subscriptionGuard;
        this.payWayService = payWayService;
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
        if (!subscriptionGuard.isEnforcementEnabled()) {
            return new Result(0, 0);
        }

        int graceDays = Integer.parseInt(payWayService.getSetting("grace_period_days", "3"));
        // One bounded scan covers both reminders (endsAt within 8 days) and expired owners (endsAt in the past).
        List<OwnerAccount> candidates = ownerAccountRepository
                .findBySubscriptionEndsAtBeforeAndSubscriptionStatusNotIn(
                        now.plus(Duration.ofDays(8)), SKIP_STATUSES);

        int remindersSent = 0;
        int expiredCount = 0;
        for (OwnerAccount owner : candidates) {
            Instant endsAt = owner.getSubscriptionEndsAt();
            if (endsAt == null) {
                continue;
            }

            if (now.isBefore(endsAt)) {
                long daysRemaining = Duration.between(now, endsAt).toDays();
                if (REMINDER_DAYS.contains(daysRemaining)) {
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

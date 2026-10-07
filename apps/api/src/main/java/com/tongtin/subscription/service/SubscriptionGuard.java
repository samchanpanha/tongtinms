package com.tongtin.subscription.service;

import com.tongtin.common.errors.ForbiddenException;
import com.tongtin.groups.repository.GroupRepository;
import com.tongtin.identity.entity.OwnerAccount;
import com.tongtin.identity.repository.OwnerAccountRepository;
import com.tongtin.subscription.entity.SubscriptionPlan;
import com.tongtin.subscription.repository.SubscriptionPlanRepository;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;

/**
 * Single place where host subscription policy is enforced for host operations.
 * Grace period and the enforcement switch are read from system settings so the
 * admin configuration actually drives behavior.
 */
@Service
public class SubscriptionGuard {

    private final OwnerAccountRepository ownerAccountRepository;
    private final SubscriptionPlanRepository planRepository;
    private final GroupRepository groupRepository;
    private final PayWayService payWayService;

    public SubscriptionGuard(
            OwnerAccountRepository ownerAccountRepository,
            SubscriptionPlanRepository planRepository,
            GroupRepository groupRepository,
            PayWayService payWayService) {
        this.ownerAccountRepository = ownerAccountRepository;
        this.planRepository = planRepository;
        this.groupRepository = groupRepository;
        this.payWayService = payWayService;
    }

    public boolean isEnforcementEnabled() {
        return Boolean.parseBoolean(payWayService.getSetting("enforce_subscription", "true"));
    }

    public void assertCanCreateGroup(Long ownerId) {
        if (!isEnforcementEnabled()) {
            return;
        }

        OwnerAccount owner = ownerAccountRepository.findById(ownerId).orElse(null);
        if (owner == null || "LIFETIME".equalsIgnoreCase(owner.getSubscriptionStatus())) {
            return;
        }

        Instant now = Instant.now();
        Instant endsAt = owner.getSubscriptionEndsAt() != null ? owner.getSubscriptionEndsAt() : now;
        if (now.isAfter(endsAt)) {
            int graceDays = Integer.parseInt(payWayService.getSetting("grace_period_days", "3"));
            if (now.isAfter(endsAt.plus(Duration.ofDays(graceDays)))) {
                throw new ForbiddenException(
                        "Gói sử dụng của bạn đã hết hạn. Vui lòng gia hạn gói để tạo thêm dây hụi mới.");
            }
        }

        if (owner.getCurrentPlanId() != null) {
            SubscriptionPlan plan = planRepository.findById(owner.getCurrentPlanId()).orElse(null);
            if (plan != null && plan.getMaxGroups() >= 0) {
                long groupsCount = groupRepository.countByOwnerId(ownerId);
                if (groupsCount >= plan.getMaxGroups()) {
                    throw new ForbiddenException("Bạn đã đạt giới hạn " + plan.getMaxGroups()
                            + " dây hụi của gói '" + plan.getName() + "'. Vui lòng nâng cấp gói để tạo thêm.");
                }
            }
        }
    }
}

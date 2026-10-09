package com.tongtin.subscription.dto;

import java.time.Instant;

public record SubscriptionStatusResponse(
        Long ownerId,
        String displayName,
        String subscriptionStatus,    // TRIAL | ACTIVE | GRACE_PERIOD | EXPIRED | LIFETIME
        Instant trialEndsAt,
        Instant subscriptionEndsAt,
        long daysRemaining,
        boolean isTrial,
        boolean isGracePeriod,
        boolean isExpired,
        boolean subscriptionEnabled, // master switch (subscription_enabled setting)
        boolean canCreateGroup,
        SubscriptionPlanDto currentPlan,
        long groupsCount,
        int maxGroups,
        long membersCount,
        int maxMembers
) {}

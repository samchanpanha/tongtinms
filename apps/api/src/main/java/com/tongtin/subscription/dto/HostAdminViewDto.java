package com.tongtin.subscription.dto;

import java.time.Instant;

public record HostAdminViewDto(
        Long ownerId,
        Long userId,
        String fullName,
        String phone,
        String email,
        String displayName,
        String subscriptionStatus,
        Instant trialEndsAt,
        Instant subscriptionEndsAt,
        long daysRemaining,
        Long currentPlanId,
        String currentPlanName,
        long groupsCount,
        long membersCount,
        Instant registeredAt
) {}

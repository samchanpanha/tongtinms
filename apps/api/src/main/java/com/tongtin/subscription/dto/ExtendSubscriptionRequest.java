package com.tongtin.subscription.dto;

import jakarta.validation.constraints.NotNull;

public record ExtendSubscriptionRequest(
        @NotNull Integer extendDays,
        Long planId,
        String reason,
        Boolean setLifetime
) {}

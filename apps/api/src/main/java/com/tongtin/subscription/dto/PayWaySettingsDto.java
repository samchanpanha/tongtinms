package com.tongtin.subscription.dto;

public record PayWaySettingsDto(
        String merchantId,
        String apiKey,
        String apiUrl,
        String checkUrl,
        boolean sandboxMode,
        boolean enabled,
        int freeTrialDays,
        int gracePeriodDays,
        boolean enforceSubscription
) {}

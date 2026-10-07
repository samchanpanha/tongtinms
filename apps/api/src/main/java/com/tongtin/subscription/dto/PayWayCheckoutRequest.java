package com.tongtin.subscription.dto;

import jakarta.validation.constraints.NotNull;

public record PayWayCheckoutRequest(
        @NotNull Long planId,
        String paymentOption, // cards | abapay_khqr | abapay_deeplink | null
        String returnUrl,
        String continueSuccessUrl,
        String cancelUrl
) {}

package com.tongtin.subscription.dto;

import java.util.Map;

public record PayWayCheckoutResponse(
        String tranId,
        String reqTime,
        String merchantId,
        String amount,
        String currency,
        String itemsBase64,
        String hash,
        String checkoutUrl,
        String paymentOption,
        String returnUrl,
        String continueSuccessUrl,
        String cancelUrl,
        String firstName,
        String lastName,
        String email,
        String phone,
        String qrString,
        Map<String, String> formFields
) {}

package com.tongtin.subscription.service;

import com.tongtin.subscription.entity.SystemSetting;
import com.tongtin.subscription.repository.SystemSettingRepository;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PayWayService {

    private static final Logger log = LoggerFactory.getLogger(PayWayService.class);
    private static final DateTimeFormatter REQ_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final SystemSettingRepository systemSettingRepository;

    public PayWayService(SystemSettingRepository systemSettingRepository) {
        this.systemSettingRepository = systemSettingRepository;
    }

    public String getSetting(String key, String defaultValue) {
        return systemSettingRepository.findById(key)
                .map(SystemSetting::getValue)
                .orElse(defaultValue);
    }

    public String getMerchantId() {
        return getSetting("payway_merchant_id", "ec438992");
    }

    public String getApiKey() {
        return getSetting("payway_api_key", "4c05336bf1f621375d86242aebe3daee7fa5a1c3");
    }

    public String getApiUrl() {
        return getSetting("payway_api_url", "https://checkout-sandbox.payway.com.kh/api/payment-gateway/v1/payments/purchase");
    }

    public String getCheckUrl() {
        return getSetting("payway_check_url", "https://checkout-sandbox.payway.com.kh/api/payment-gateway/v1/payments/check-transaction");
    }

    public boolean isSandbox() {
        return Boolean.parseBoolean(getSetting("payway_sandbox_mode", "true"));
    }

    public boolean isEnabled() {
        return Boolean.parseBoolean(getSetting("payway_enabled", "true"));
    }

    public String generateReqTime() {
        return LocalDateTime.now().format(REQ_TIME_FORMATTER);
    }

    public String formatAmount(long amountMinor, String currency) {
        int exponent = switch (currency.toUpperCase()) {
            case "USD", "SGD", "THB" -> 2;
            case "VND", "KHR", "LAK" -> 0;
            default -> 2;
        };

        if (exponent == 0) {
            return String.valueOf(amountMinor);
        } else {
            double value = (double) amountMinor / Math.pow(10, exponent);
            DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
            DecimalFormat df = new DecimalFormat("0.00", symbols);
            return df.format(value);
        }
    }

    /**
     * Compute ABA PayWay Purchase HMAC-SHA512 Hash according to official documentation:
     * concatenated: req_time + merchant_id + tran_id + amount + items + shipping + first_name +
     * last_name + email + phone + type + payment_option + return_url + cancel_url + continue_success_url + return_params
     */
    public String generatePurchaseHash(
            String reqTime,
            String merchantId,
            String tranId,
            String amount,
            String items,
            String shipping,
            String firstName,
            String lastName,
            String email,
            String phone,
            String type,
            String paymentOption,
            String returnUrl,
            String cancelUrl,
            String continueSuccessUrl,
            String returnParams) {

        StringBuilder raw = new StringBuilder();
        appendIfNotNull(raw, reqTime);
        appendIfNotNull(raw, merchantId);
        appendIfNotNull(raw, tranId);
        appendIfNotNull(raw, amount);
        appendIfNotNull(raw, items);
        appendIfNotNull(raw, shipping);
        appendIfNotNull(raw, firstName);
        appendIfNotNull(raw, lastName);
        appendIfNotNull(raw, email);
        appendIfNotNull(raw, phone);
        appendIfNotNull(raw, type);
        appendIfNotNull(raw, paymentOption);
        appendIfNotNull(raw, returnUrl);
        appendIfNotNull(raw, cancelUrl);
        appendIfNotNull(raw, continueSuccessUrl);
        appendIfNotNull(raw, returnParams);

        return hmacSha512Base64(raw.toString(), getApiKey());
    }

    /**
     * Compute ABA PayWay Check Transaction Hash:
     * req_time + merchant_id + tran_id
     */
    public String generateCheckTransactionHash(String reqTime, String merchantId, String tranId) {
        String raw = (reqTime != null ? reqTime : "") +
                (merchantId != null ? merchantId : "") +
                (tranId != null ? tranId : "");
        return hmacSha512Base64(raw, getApiKey());
    }

    public String hmacSha512Base64(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
            mac.init(secretKeySpec);
            byte[] hashBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashBytes);
        } catch (NoSuchAlgorithmException | java.security.InvalidKeyException e) {
            log.error("Failed to compute HMAC-SHA512: {}", e.getMessage(), e);
            throw new IllegalStateException("Crypto error computing PayWay hash", e);
        }
    }

    private void appendIfNotNull(StringBuilder sb, String value) {
        if (value != null) {
            sb.append(value);
        }
    }
}

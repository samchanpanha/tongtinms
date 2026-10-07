package com.tongtin.subscription.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongtin.settings.service.SettingsService;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PayWayService {

    private static final Logger log = LoggerFactory.getLogger(PayWayService.class);
    private static final DateTimeFormatter REQ_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final SettingsService settingsService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public PayWayService(SettingsService settingsService, ObjectMapper objectMapper) {
        this.settingsService = settingsService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /** Result of an authoritative Check-Transaction call against ABA PayWay. */
    public record TransactionCheck(boolean approved, String gatewayTranId, String rawJson) {
    }

    public String getSetting(String key, String defaultValue) {
        return settingsService.getString(key, defaultValue);
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
        return settingsService.getBoolean("payway_sandbox_mode", true);
    }

    public boolean isEnabled() {
        return settingsService.getBoolean("payway_enabled", true);
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
        }
        return BigDecimal.valueOf(amountMinor, exponent).toPlainString();
    }

    /**
     * Authoritative verification: asks ABA PayWay (check-transaction API) whether the order
     * was actually paid. Callers must never activate a subscription based on a callback's
     * URL parameters alone — only on this response.
     */
    public TransactionCheck checkTransaction(String reqTime, String merchantId, String tranId) {
        if (reqTime == null || reqTime.isBlank() || tranId == null || tranId.isBlank()) {
            return new TransactionCheck(false, null, "{\"error\":\"missing req_time or tran_id\"}");
        }
        String hash = generateCheckTransactionHash(reqTime, merchantId, tranId);

        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("req_time", reqTime);
        payload.put("merchant_id", merchantId);
        payload.put("tran_id", tranId);
        payload.put("hash", hash);

        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(getCheckUrl()))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("PayWay check-transaction returned HTTP {} for {}", response.statusCode(), tranId);
                return new TransactionCheck(false, null, response.body());
            }

            JsonNode root = objectMapper.readTree(response.body());
            String code = root.path("status").path("code").asText("");
            String paymentStatus = root.path("data").path("payment_status").asText("");
            String gatewayTranId = root.path("data").path("tran_id").asText(tranId);

            boolean statusOk = "0".equals(code) || "00".equals(code);
            boolean approved = statusOk && "APPROVED".equalsIgnoreCase(paymentStatus);
            if (!approved) {
                log.info("PayWay check-transaction NOT approved for {}: status.code={}, payment_status={}",
                        tranId, code, paymentStatus);
            }
            return new TransactionCheck(approved, gatewayTranId, response.body());
        } catch (Exception e) {
            log.error("PayWay check-transaction failed for {}: {}", tranId, e.getMessage());
            return new TransactionCheck(false, null, "{\"error\":\"" + e.getClass().getSimpleName() + "\"}");
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

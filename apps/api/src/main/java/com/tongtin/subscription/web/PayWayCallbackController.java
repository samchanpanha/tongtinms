package com.tongtin.subscription.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongtin.settings.service.SettingsService;
import com.tongtin.subscription.entity.SubscriptionOrder;
import com.tongtin.subscription.service.CallbackRateLimiter;
import com.tongtin.subscription.service.PayWayService;
import com.tongtin.subscription.service.PaymentEventService;
import com.tongtin.subscription.service.SubscriptionService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments/payway")
public class PayWayCallbackController {

    private static final Logger log = LoggerFactory.getLogger(PayWayCallbackController.class);

    private final SubscriptionService subscriptionService;
    private final PayWayService payWayService;
    private final PaymentEventService paymentEventService;
    private final CallbackRateLimiter rateLimiter;
    private final SettingsService settingsService;
    private final ObjectMapper objectMapper;
    private final int defaultCallbackLimit;

    public PayWayCallbackController(
            SubscriptionService subscriptionService,
            PayWayService payWayService,
            PaymentEventService paymentEventService,
            CallbackRateLimiter rateLimiter,
            SettingsService settingsService,
            ObjectMapper objectMapper,
            @Value("${app.rate-limit.callback-per-minute:60}") int defaultCallbackLimit) {
        this.subscriptionService = subscriptionService;
        this.payWayService = payWayService;
        this.paymentEventService = paymentEventService;
        this.rateLimiter = rateLimiter;
        this.settingsService = settingsService;
        this.objectMapper = objectMapper;
        this.defaultCallbackLimit = defaultCallbackLimit;
    }

    /**
     * ABA PayWay Webhook callback endpoint according to developer.payway.com.kh.
     * The callback parameters are never trusted on their own: activation happens only
     * after the check-transaction API confirms the payment with ABA PayWay.
     * Every invocation is journaled to the append-only payment_events table and
     * limited per IP (rate_limit_payway_callback_per_minute, default 60/min).
     */
    @PostMapping("/callback")
    public ResponseEntity<Map<String, Object>> handlePayWayCallback(
            @RequestParam(required = false) String tran_id,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String apv,
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest request) {

        String effectiveTranId = tran_id != null ? tran_id : (body != null ? (String) body.get("tran_id") : null);
        String effectiveStatus = status != null ? status : (body != null ? String.valueOf(body.get("status")) : "0");
        String clientIp = clientIp(request);
        String payload = buildPayload(effectiveTranId, effectiveStatus, apv, body, null);

        log.info("Received ABA PayWay webhook callback: tran_id={}, status={}", effectiveTranId, effectiveStatus);

        int limit = settingsService.getInt("rate_limit_payway_callback_per_minute", defaultCallbackLimit);
        if (!rateLimiter.allow(clientIp, limit, Duration.ofMinutes(1))) {
            paymentEventService.record(effectiveTranId, "CALLBACK", "RATE_LIMITED", payload, clientIp);
            log.warn("PayWay callback rate limit exceeded for {}", clientIp);
            return ResponseEntity.status(429)
                    .body(Map.of("status", 429, "message", "rate limit exceeded for callback"));
        }

        if (effectiveTranId == null || effectiveTranId.isBlank()) {
            paymentEventService.record(null, "CALLBACK", "MISSING_TRAN_ID", payload, clientIp);
            return ResponseEntity.badRequest().body(Map.of("status", 1, "message", "Missing tran_id"));
        }

        try {
            SubscriptionOrder order = subscriptionService.verifyAndActivateViaGateway(effectiveTranId);
            paymentEventService.record(effectiveTranId, "CALLBACK", "ORDER_" + order.getStatus(), payload, clientIp);
            return ResponseEntity.ok(Map.of(
                    "status", 0,
                    "description", "Success",
                    "orderStatus", order.getStatus()));
        } catch (Exception ex) {
            log.error("Failed to process PayWay callback for {}: {}", effectiveTranId, ex.getMessage());
            paymentEventService.record(effectiveTranId, "CALLBACK", "ERROR",
                    buildPayload(effectiveTranId, effectiveStatus, apv, body, ex.getMessage()), clientIp);
            return ResponseEntity.badRequest().body(Map.of("status", 1, "message", ex.getMessage()));
        }
    }

    /**
     * Development / Sandbox simulation endpoint for testing the entire PayWay payment flow.
     * Hard-disabled outside sandbox mode so it can never be used to obtain a free
     * subscription in production.
     */
    @PostMapping("/simulate-complete")
    public ResponseEntity<Map<String, Object>> simulateComplete(@RequestBody Map<String, String> body) {
        if (!payWayService.isSandbox()) {
            return ResponseEntity.status(403).body(Map.of(
                    "status", 1,
                    "message", "Payment simulation is disabled outside sandbox mode"));
        }

        String tranId = body.get("tranId");
        if (tranId == null || tranId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("status", 1, "message", "Missing tranId"));
        }

        SubscriptionOrder order = subscriptionService.verifyAndActivateOrder(
                tranId,
                "SIM_ABA_" + System.currentTimeMillis(),
                "{\"status\":0,\"payment_status\":\"APPROVED\",\"type\":\"cards/khqr_simulation\"}"
        );

        return ResponseEntity.ok(Map.of(
                "status", 0,
                "message", "Payment simulated successfully",
                "tranId", order.getTranId(),
                "orderStatus", order.getStatus()
        ));
    }

    private String buildPayload(String tranId, String status, String apv,
            Map<String, Object> body, String error) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("tran_id", tranId);
        envelope.put("status", status);
        envelope.put("apv", apv);
        envelope.put("body", body);
        if (error != null) {
            envelope.put("error", error);
        }
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (Exception ex) {
            return "{\"error\":\"unserializable_callback_payload\"}";
        }
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}

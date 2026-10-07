package com.tongtin.subscription.web;

import com.tongtin.subscription.entity.SubscriptionOrder;
import com.tongtin.subscription.service.PayWayService;
import com.tongtin.subscription.service.SubscriptionService;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    public PayWayCallbackController(SubscriptionService subscriptionService, PayWayService payWayService) {
        this.subscriptionService = subscriptionService;
        this.payWayService = payWayService;
    }

    /**
     * ABA PayWay Webhook callback endpoint according to developer.payway.com.kh
     */
    @PostMapping("/callback")
    public ResponseEntity<Map<String, Object>> handlePayWayCallback(
            @RequestParam(required = false) String tran_id,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String apv,
            @RequestBody(required = false) Map<String, Object> body) {

        String effectiveTranId = tran_id != null ? tran_id : (body != null ? (String) body.get("tran_id") : null);
        String effectiveStatus = status != null ? status : (body != null ? String.valueOf(body.get("status")) : "0");

        log.info("Received ABA PayWay webhook callback: tran_id={}, status={}", effectiveTranId, effectiveStatus);

        if (effectiveTranId == null || effectiveTranId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("status", 1, "message", "Missing tran_id"));
        }

        try {
            subscriptionService.verifyAndActivateOrder(
                    effectiveTranId,
                    apv != null ? apv : "WEBHOOK_" + System.currentTimeMillis(),
                    body != null ? body.toString() : "{\"status\":0}"
            );
            return ResponseEntity.ok(Map.of("status", 0, "description", "Success"));
        } catch (Exception ex) {
            log.error("Failed to process PayWay callback for {}: {}", effectiveTranId, ex.getMessage());
            return ResponseEntity.badRequest().body(Map.of("status", 1, "message", ex.getMessage()));
        }
    }

    /**
     * Development / Sandbox simulation endpoint for testing the entire PayWay payment flow
     */
    @PostMapping("/simulate-complete")
    public ResponseEntity<Map<String, Object>> simulateComplete(@RequestBody Map<String, String> body) {
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
}

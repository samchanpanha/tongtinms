package com.tongtin.subscription.web;

import com.tongtin.common.security.AuthPrincipal;
import com.tongtin.subscription.dto.PayWayCheckoutRequest;
import com.tongtin.subscription.dto.PayWayCheckoutResponse;
import com.tongtin.subscription.dto.SubscriptionPlanDto;
import com.tongtin.subscription.dto.SubscriptionStatusResponse;
import com.tongtin.subscription.entity.SubscriptionOrder;
import com.tongtin.subscription.service.SubscriptionService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/subscription")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    public SubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @GetMapping("/plans")
    public List<SubscriptionPlanDto> getActivePlans() {
        return subscriptionService.getActivePlans();
    }

    @GetMapping("/my-status")
    @PreAuthorize("hasRole('HOST')")
    public SubscriptionStatusResponse getMyStatus(@AuthenticationPrincipal AuthPrincipal principal) {
        return subscriptionService.getMyStatus(principal.ownerId());
    }

    @PostMapping("/checkout/payway")
    @PreAuthorize("hasRole('HOST')")
    public PayWayCheckoutResponse checkoutPayWay(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody PayWayCheckoutRequest request) {
        return subscriptionService.checkoutPayWay(principal.userId(), principal.ownerId(), request);
    }

    @PostMapping("/verify/{tranId}")
    @PreAuthorize("hasRole('HOST')")
    public ResponseEntity<SubscriptionOrder> verifyOrder(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable String tranId) {
        SubscriptionOrder order = subscriptionService.verifyOrderForOwner(principal.ownerId(), tranId);
        return ResponseEntity.ok(order);
    }

    @GetMapping("/invoices")
    @PreAuthorize("hasRole('HOST')")
    public List<SubscriptionOrder> getInvoices(@AuthenticationPrincipal AuthPrincipal principal) {
        return subscriptionService.getHostInvoices(principal.ownerId());
    }
}

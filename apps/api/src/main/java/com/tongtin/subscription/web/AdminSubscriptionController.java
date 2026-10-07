package com.tongtin.subscription.web;

import com.tongtin.common.security.AuthPrincipal;
import com.tongtin.subscription.dto.ExtendSubscriptionRequest;
import com.tongtin.subscription.dto.HostAdminViewDto;
import com.tongtin.subscription.dto.PayWaySettingsDto;
import com.tongtin.subscription.dto.PlanCreateUpdateRequest;
import com.tongtin.subscription.dto.SubscriptionPlanDto;
import com.tongtin.subscription.entity.SubscriptionOrder;
import com.tongtin.subscription.service.SubscriptionService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminSubscriptionController {

    private final SubscriptionService subscriptionService;

    public AdminSubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    // 1. Subscription Plans
    @GetMapping("/plans")
    public List<SubscriptionPlanDto> getAllPlans() {
        return subscriptionService.getAllPlansAdmin();
    }

    @PostMapping("/plans")
    public ResponseEntity<SubscriptionPlanDto> createPlan(@Valid @RequestBody PlanCreateUpdateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(subscriptionService.createPlan(request));
    }

    @PutMapping("/plans/{id}")
    public SubscriptionPlanDto updatePlan(
            @PathVariable Long id,
            @Valid @RequestBody PlanCreateUpdateRequest request) {
        return subscriptionService.updatePlan(id, request);
    }

    @DeleteMapping("/plans/{id}")
    public ResponseEntity<Void> deletePlan(@PathVariable Long id) {
        subscriptionService.deletePlan(id);
        return ResponseEntity.noContent().build();
    }

    // 2. Host Directory & Subscriptions
    @GetMapping("/hosts")
    public List<HostAdminViewDto> getHosts() {
        return subscriptionService.getAdminHostsList();
    }

    @PostMapping("/hosts/{id}/extend")
    public ResponseEntity<HostAdminViewDto> extendSubscription(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody ExtendSubscriptionRequest request) {
        return ResponseEntity.ok(subscriptionService.extendSubscription(principal.userId(), id, request));
    }

    // 3. Transactions / Orders
    @GetMapping("/orders")
    public List<SubscriptionOrder> getOrders() {
        return subscriptionService.getAdminOrdersList();
    }

    // 4. PayWay Settings & Configuration
    @GetMapping("/settings/payway")
    public PayWaySettingsDto getPayWaySettings() {
        return subscriptionService.getPayWaySettings();
    }

    @PutMapping("/settings/payway")
    public PayWaySettingsDto updatePayWaySettings(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody PayWaySettingsDto request) {
        return subscriptionService.updatePayWaySettings(principal.userId(), request);
    }
}

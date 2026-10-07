package com.tongtin.subscription.service;

import com.tongtin.common.errors.BadRequestException;
import com.tongtin.common.errors.ConflictException;
import com.tongtin.common.errors.ForbiddenException;
import com.tongtin.common.errors.NotFoundException;
import com.tongtin.groups.repository.GroupRepository;
import com.tongtin.identity.entity.AuditEvent;
import com.tongtin.identity.entity.OwnerAccount;
import com.tongtin.identity.entity.User;
import com.tongtin.identity.repository.AuditEventRepository;
import com.tongtin.identity.repository.OwnerAccountRepository;
import com.tongtin.identity.repository.UserRepository;
import com.tongtin.members.repository.MemberProfileRepository;
import com.tongtin.notify.service.NotificationService;
import com.tongtin.settings.service.SettingsService;
import com.tongtin.subscription.dto.ExtendSubscriptionRequest;
import com.tongtin.subscription.dto.HostAdminViewDto;
import com.tongtin.subscription.dto.PayWayCheckoutRequest;
import com.tongtin.subscription.dto.PayWayCheckoutResponse;
import com.tongtin.subscription.dto.PayWaySettingsDto;
import com.tongtin.subscription.dto.PlanCreateUpdateRequest;
import com.tongtin.subscription.dto.SubscriptionPlanDto;
import com.tongtin.subscription.dto.SubscriptionStatusResponse;
import com.tongtin.subscription.entity.SubscriptionOrder;
import com.tongtin.subscription.entity.SubscriptionPlan;
import com.tongtin.subscription.repository.SubscriptionOrderRepository;
import com.tongtin.subscription.repository.SubscriptionPlanRepository;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionService.class);

    private final SubscriptionPlanRepository planRepository;
    private final SubscriptionOrderRepository orderRepository;
    private final SettingsService settingsService;
    private final OwnerAccountRepository ownerAccountRepository;
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final MemberProfileRepository memberProfileRepository;
    private final AuditEventRepository auditEventRepository;
    private final NotificationService notificationService;
    private final PayWayService payWayService;

    public SubscriptionService(
            SubscriptionPlanRepository planRepository,
            SubscriptionOrderRepository orderRepository,
            SettingsService settingsService,
            OwnerAccountRepository ownerAccountRepository,
            UserRepository userRepository,
            GroupRepository groupRepository,
            MemberProfileRepository memberProfileRepository,
            AuditEventRepository auditEventRepository,
            NotificationService notificationService,
            PayWayService payWayService) {
        this.planRepository = planRepository;
        this.orderRepository = orderRepository;
        this.settingsService = settingsService;
        this.ownerAccountRepository = ownerAccountRepository;
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.memberProfileRepository = memberProfileRepository;
        this.auditEventRepository = auditEventRepository;
        this.notificationService = notificationService;
        this.payWayService = payWayService;
    }

    // ==========================================
    // 1. Host Subscription Status
    // ==========================================
    @Transactional(readOnly = true)
    public SubscriptionStatusResponse getMyStatus(Long ownerId) {
        OwnerAccount owner = ownerAccountRepository.findById(ownerId)
                .orElseThrow(() -> new NotFoundException("Owner account not found"));

        Instant now = Instant.now();
        Instant endsAt = owner.getSubscriptionEndsAt() != null ? owner.getSubscriptionEndsAt() : now;
        Instant trialEnd = owner.getTrialEndsAt() != null ? owner.getTrialEndsAt() : now;

        long secondsRemaining = endsAt.getEpochSecond() - now.getEpochSecond();
        long daysRemaining = Math.max(0, (secondsRemaining + 86399) / 86400);

        int gracePeriodDays = settingsService.getInt("grace_period_days", 3);
        Instant graceEnd = endsAt.plus(Duration.ofDays(gracePeriodDays));

        boolean isLifetime = "LIFETIME".equalsIgnoreCase(owner.getSubscriptionStatus());
        boolean isTrial = "TRIAL".equalsIgnoreCase(owner.getSubscriptionStatus()) && now.isBefore(trialEnd);
        boolean isGracePeriod = !isLifetime && now.isAfter(endsAt) && now.isBefore(graceEnd);
        boolean isExpired = !isLifetime && now.isAfter(graceEnd);

        String effectiveStatus = isLifetime ? "LIFETIME" :
                (isExpired ? "EXPIRED" : (isGracePeriod ? "GRACE_PERIOD" : (isTrial ? "TRIAL" : "ACTIVE")));

        long groupsCount = groupRepository.countByOwnerId(ownerId);

        long membersCount = memberProfileRepository.countByOwnerId(ownerId);

        SubscriptionPlanDto currentPlanDto = null;
        int maxGroups = -1;
        int maxMembers = -1;

        if (owner.getCurrentPlanId() != null) {
            SubscriptionPlan plan = planRepository.findById(owner.getCurrentPlanId()).orElse(null);
            if (plan != null) {
                currentPlanDto = SubscriptionPlanDto.fromEntity(plan);
                maxGroups = plan.getMaxGroups();
                maxMembers = plan.getMaxMembers();
            }
        }

        boolean canCreateGroup = !isExpired && (maxGroups < 0 || groupsCount < maxGroups);

        return new SubscriptionStatusResponse(
                owner.getId(),
                owner.getDisplayName(),
                effectiveStatus,
                trialEnd,
                endsAt,
                daysRemaining,
                isTrial,
                isGracePeriod,
                isExpired,
                canCreateGroup,
                currentPlanDto,
                groupsCount,
                maxGroups,
                membersCount,
                maxMembers
        );
    }

    // ==========================================
    // 2. Plans Management (Public & Admin)
    // ==========================================
    @Transactional(readOnly = true)
    public List<SubscriptionPlanDto> getActivePlans() {
        return planRepository.findByIsActiveTrueOrderBySortOrderAsc().stream()
                .map(SubscriptionPlanDto::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SubscriptionPlanDto> getAllPlansAdmin() {
        return planRepository.findAllByOrderBySortOrderAsc().stream()
                .map(SubscriptionPlanDto::fromEntity)
                .toList();
    }

    @Transactional
    public SubscriptionPlanDto createPlan(PlanCreateUpdateRequest req) {
        if (planRepository.existsByCode(req.code())) {
            throw new ConflictException("Plan code '" + req.code() + "' already exists");
        }
        SubscriptionPlan plan = new SubscriptionPlan();
        applyPlanFields(plan, req);
        planRepository.save(plan);
        return SubscriptionPlanDto.fromEntity(plan);
    }

    @Transactional
    public SubscriptionPlanDto updatePlan(Long id, PlanCreateUpdateRequest req) {
        SubscriptionPlan plan = planRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Plan not found: " + id));

        if (!plan.getCode().equals(req.code()) && planRepository.existsByCode(req.code())) {
            throw new ConflictException("Plan code '" + req.code() + "' already exists");
        }

        applyPlanFields(plan, req);
        plan.setUpdatedAt(Instant.now());
        planRepository.save(plan);
        return SubscriptionPlanDto.fromEntity(plan);
    }

    @Transactional
    public void deletePlan(Long id) {
        SubscriptionPlan plan = planRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Plan not found: " + id));
        planRepository.delete(plan);
    }

    private void applyPlanFields(SubscriptionPlan plan, PlanCreateUpdateRequest req) {
        plan.setCode(req.code().trim());
        plan.setName(req.name().trim());
        plan.setDescription(req.description() != null ? req.description().trim() : null);
        plan.setPriceMinor(req.priceMinor());
        plan.setCurrency(req.currency().trim().toUpperCase());
        plan.setDurationMonths(req.durationMonths());
        plan.setMaxGroups(req.maxGroups() != null ? req.maxGroups() : -1);
        plan.setMaxMembers(req.maxMembers() != null ? req.maxMembers() : -1);
        plan.setFeaturesJson(req.featuresJson());
        plan.setBadge(req.badge() != null && !req.badge().isBlank() ? req.badge().trim() : null);
        plan.setSortOrder(req.sortOrder() != null ? req.sortOrder() : 0);
        plan.setIsActive(req.isActive() != null ? req.isActive() : true);
    }

    // ==========================================
    // 3. ABA PayWay Checkout Initiation
    // ==========================================
    @Transactional
    public PayWayCheckoutResponse checkoutPayWay(Long userId, Long ownerId, PayWayCheckoutRequest req) {
        SubscriptionPlan plan = planRepository.findById(req.planId())
                .orElseThrow(() -> new NotFoundException("Subscription plan not found"));

        if (!Boolean.TRUE.equals(plan.getIsActive())) {
            throw new BadRequestException("Selected subscription plan is inactive");
        }

        if (!payWayService.isEnabled()) {
            throw new BadRequestException("ABA PayWay payment gateway is currently disabled");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        OwnerAccount owner = ownerAccountRepository.findById(ownerId)
                .orElseThrow(() -> new NotFoundException("Owner not found"));

        String reqTime = payWayService.generateReqTime();
        // Unguessable suffix: tran_id itself must not be enumerable.
        String tranId = "TT_" + ownerId + "_"
                + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 10);

        String merchantId = payWayService.getMerchantId();
        String amountStr = payWayService.formatAmount(plan.getPriceMinor(), plan.getCurrency());

        // Base64 JSON items
        String itemsJson = String.format("[{\"name\":\"%s\",\"quantity\":1,\"price\":\"%s\"}]",
                plan.getName().replace("\"", "\\\""), amountStr);
        String itemsBase64 = Base64.getEncoder().encodeToString(itemsJson.getBytes(StandardCharsets.UTF_8));

        String firstName = user.getFullName() != null && !user.getFullName().isBlank() ? user.getFullName() : "Host";
        String lastName = owner.getDisplayName() != null ? owner.getDisplayName() : "";
        String email = user.getEmail() != null ? user.getEmail() : "host" + ownerId + "@tongtin.app";
        String phone = user.getPhone();
        String type = "purchase";
        String paymentOption = req.paymentOption() != null && !req.paymentOption().isBlank()
                ? req.paymentOption()
                : "cards,abapay_khqr,abapay_deeplink";

        String returnUrl = req.returnUrl() != null ? req.returnUrl() : "/host/subscription?status=success&tran_id=" + tranId;
        String continueSuccessUrl = req.continueSuccessUrl() != null ? req.continueSuccessUrl() : "/host/subscription?status=success";
        String cancelUrl = req.cancelUrl() != null ? req.cancelUrl() : "/host/subscription?status=cancelled";

        String hash = payWayService.generatePurchaseHash(
                reqTime,
                merchantId,
                tranId,
                amountStr,
                itemsBase64,
                "", // shipping
                firstName,
                lastName,
                email,
                phone,
                type,
                paymentOption,
                returnUrl,
                cancelUrl,
                continueSuccessUrl,
                "" // returnParams
        );

        // Save order in database
        SubscriptionOrder order = new SubscriptionOrder();
        order.setOwnerId(ownerId);
        order.setPlanId(plan.getId());
        order.setTranId(tranId);
        order.setAmountMinor(plan.getPriceMinor());
        order.setCurrency(plan.getCurrency());
        order.setStatus("PENDING");
        order.setPaymentGateway("ABA_PAYWAY");
        order.setReqTime(reqTime);
        order.setPaywayHash(hash);
        orderRepository.save(order);

        // Prepare ABA KHQR payload string simulation / standard KHQR format
        String qrString = String.format("00020101021229370016abaa%s%s520459995303840540%s5802KH59%02d%s6010PHNOM PENH62200716%s6304%s",
                merchantId, tranId, amountStr, firstName.length(), firstName, tranId, hash.substring(0, 4).toUpperCase());

        Map<String, String> formFields = new LinkedHashMap<>();
        formFields.put("req_time", reqTime);
        formFields.put("merchant_id", merchantId);
        formFields.put("tran_id", tranId);
        formFields.put("amount", amountStr);
        formFields.put("items", itemsBase64);
        formFields.put("first_name", firstName);
        formFields.put("last_name", lastName);
        formFields.put("email", email);
        formFields.put("phone", phone);
        formFields.put("type", type);
        formFields.put("payment_option", paymentOption);
        formFields.put("return_url", returnUrl);
        formFields.put("cancel_url", cancelUrl);
        formFields.put("continue_success_url", continueSuccessUrl);
        formFields.put("hash", hash);

        return new PayWayCheckoutResponse(
                tranId,
                reqTime,
                merchantId,
                amountStr,
                plan.getCurrency(),
                itemsBase64,
                hash,
                payWayService.getApiUrl(),
                paymentOption,
                returnUrl,
                continueSuccessUrl,
                cancelUrl,
                firstName,
                lastName,
                email,
                phone,
                qrString,
                formFields
        );
    }

    // ==========================================
    // 4. Verify & Activate Order
    // ==========================================
    @Transactional
    public SubscriptionOrder verifyAndActivateOrder(String tranId, String gatewayTranId, String gatewayResponse) {
        SubscriptionOrder order = orderRepository.findByTranId(tranId)
                .orElseThrow(() -> new NotFoundException("Subscription order not found: " + tranId));

        if ("PAID".equals(order.getStatus())) {
            return order;
        }

        SubscriptionPlan plan = planRepository.findById(order.getPlanId())
                .orElseThrow(() -> new NotFoundException("Plan not found: " + order.getPlanId()));

        OwnerAccount owner = ownerAccountRepository.findById(order.getOwnerId())
                .orElseThrow(() -> new NotFoundException("Owner not found: " + order.getOwnerId()));

        // Mark order as PAID
        order.setStatus("PAID");
        order.setGatewayTranId(gatewayTranId != null ? gatewayTranId : "ABA_" + tranId);
        order.setGatewayResponseJson(gatewayResponse != null ? gatewayResponse : "{\"status\":0,\"message\":\"Approved\"}");
        order.setPaidAt(Instant.now());
        orderRepository.save(order);

        // Extend Owner's subscription
        Instant currentEnd = owner.getSubscriptionEndsAt();
        Instant now = Instant.now();
        Instant base = (currentEnd != null && currentEnd.isAfter(now)) ? currentEnd : now;
        long durationDays = (long) plan.getDurationMonths() * 30L;
        Instant newEnd = base.plus(Duration.ofDays(durationDays));

        owner.setSubscriptionStatus("ACTIVE");
        owner.setSubscriptionEndsAt(newEnd);
        owner.setCurrentPlanId(plan.getId());
        ownerAccountRepository.save(owner);

        // Audit Event
        auditEventRepository.save(AuditEvent.of(
                owner.getUserId(), "OwnerAccount", String.valueOf(owner.getId()), "SUBSCRIPTION_ACTIVATED",
                String.format("{\"planId\":%d,\"planCode\":\"%s\",\"tranId\":\"%s\",\"endsAt\":\"%s\"}",
                        plan.getId(), plan.getCode(), tranId, newEnd.toString())));

        // Notification to Host
        notificationService.notifyUser(
                owner.getUserId(),
                "SUBSCRIPTION_ACTIVATED",
                "Kích hoạt gói dịch vụ thành công! / Subscription Activated",
                String.format("Bạn đã thanh toán thành công gói '%s' qua ABA PayWay. Thời hạn sử dụng đến %s.",
                        plan.getName(), newEnd.toString())
        );

        log.info("Subscription order {} activated for owner {}: new expiry {}", tranId, owner.getId(), newEnd);
        return order;
    }

    /**
     * Gateway-verified activation: only activates after ABA PayWay's check-transaction
     * API confirms the payment. Used by the public webhook callback, where request
     * parameters alone must never be trusted.
     */
    @Transactional
    public SubscriptionOrder verifyAndActivateViaGateway(String tranId) {
        SubscriptionOrder order = orderRepository.findByTranId(tranId)
                .orElseThrow(() -> new NotFoundException("Subscription order not found: " + tranId));

        if ("PAID".equals(order.getStatus())) {
            return order;
        }

        PayWayService.TransactionCheck check = payWayService.checkTransaction(
                order.getReqTime(), payWayService.getMerchantId(), tranId);

        if (!check.approved()) {
            log.warn("Order {} not approved by ABA PayWay check-transaction; leaving status {}",
                    tranId, order.getStatus());
            return order;
        }

        return verifyAndActivateOrder(tranId, check.gatewayTranId(), check.rawJson());
    }

    /**
     * Host-facing verification for the payment return page. Enforces order ownership
     * before any gateway check, so a host cannot activate or probe another owner's order.
     */
    @Transactional
    public SubscriptionOrder verifyOrderForOwner(Long ownerId, String tranId) {
        SubscriptionOrder order = orderRepository.findByTranId(tranId)
                .orElseThrow(() -> new NotFoundException("Subscription order not found: " + tranId));
        if (!order.getOwnerId().equals(ownerId)) {
            throw new ForbiddenException("You do not have access to this order");
        }
        return verifyAndActivateViaGateway(tranId);
    }

    // ==========================================
    // 5. Admin Subscription Overrides & Settings
    // ==========================================
    @Transactional(readOnly = true)
    public List<HostAdminViewDto> getAdminHostsList() {
        List<OwnerAccount> owners = ownerAccountRepository.findAll();
        List<HostAdminViewDto> list = new ArrayList<>();
        Instant now = Instant.now();

        for (OwnerAccount owner : owners) {
            User user = userRepository.findById(owner.getUserId()).orElse(null);
            if (user == null) continue;

            Instant endsAt = owner.getSubscriptionEndsAt() != null ? owner.getSubscriptionEndsAt() : now;
            long daysRemaining = Math.max(0, (endsAt.getEpochSecond() - now.getEpochSecond() + 86399) / 86400);

            String planName = "Dùng thử miễn phí";
            if (owner.getCurrentPlanId() != null) {
                SubscriptionPlan plan = planRepository.findById(owner.getCurrentPlanId()).orElse(null);
                if (plan != null) {
                    planName = plan.getName();
                }
            }

            long groupsCount = groupRepository.countByOwnerId(owner.getId());
            long membersCount = memberProfileRepository.countByOwnerId(owner.getId());

            list.add(new HostAdminViewDto(
                    owner.getId(),
                    user.getId(),
                    user.getFullName(),
                    user.getPhone(),
                    user.getEmail(),
                    owner.getDisplayName(),
                    owner.getSubscriptionStatus(),
                    owner.getTrialEndsAt(),
                    owner.getSubscriptionEndsAt(),
                    daysRemaining,
                    owner.getCurrentPlanId(),
                    planName,
                    groupsCount,
                    membersCount,
                    owner.getCreatedAt()
            ));
        }
        return list;
    }

    @Transactional
    public HostAdminViewDto extendSubscription(Long adminUserId, Long ownerId, ExtendSubscriptionRequest req) {
        OwnerAccount owner = ownerAccountRepository.findById(ownerId)
                .orElseThrow(() -> new NotFoundException("Owner not found: " + ownerId));

        Instant now = Instant.now();
        if (Boolean.TRUE.equals(req.setLifetime())) {
            owner.setSubscriptionStatus("LIFETIME");
            owner.setSubscriptionEndsAt(now.plus(Duration.ofDays(36500))); // 100 years
        } else {
            Instant currentEnd = owner.getSubscriptionEndsAt();
            Instant base = (currentEnd != null && currentEnd.isAfter(now)) ? currentEnd : now;
            owner.setSubscriptionEndsAt(base.plus(Duration.ofDays(req.extendDays())));
            owner.setSubscriptionStatus("ACTIVE");
        }

        if (req.planId() != null) {
            owner.setCurrentPlanId(req.planId());
        }

        ownerAccountRepository.save(owner);

        auditEventRepository.save(AuditEvent.of(
                adminUserId, "OwnerAccount", String.valueOf(owner.getId()), "ADMIN_EXTENDED_SUBSCRIPTION",
                String.format("{\"extendDays\":%d,\"setLifetime\":%s,\"reason\":\"%s\"}",
                        req.extendDays(), req.setLifetime(), req.reason())));

        notificationService.notifyUser(
                owner.getUserId(),
                "SUBSCRIPTION_EXTENDED",
                "Gia hạn dịch vụ từ Quản trị viên / Subscription Extended",
                String.format("Quản trị viên đã gia hạn dịch vụ thêm %d ngày cho tài khoản của bạn.", req.extendDays())
        );

        return getAdminHostsList().stream()
                .filter(h -> h.ownerId().equals(ownerId))
                .findFirst()
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<SubscriptionOrder> getAdminOrdersList() {
        return orderRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<SubscriptionOrder> getHostInvoices(Long ownerId) {
        return orderRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId);
    }

    @Transactional(readOnly = true)
    public PayWaySettingsDto getPayWaySettings() {
        return new PayWaySettingsDto(
                payWayService.getMerchantId(),
                payWayService.getApiKey(),
                payWayService.getApiUrl(),
                payWayService.getCheckUrl(),
                payWayService.isSandbox(),
                payWayService.isEnabled(),
                settingsService.getInt("free_trial_days", 30),
                settingsService.getInt("grace_period_days", 3),
                settingsService.getBoolean("enforce_subscription", true)
        );
    }

    @Transactional
    public PayWaySettingsDto updatePayWaySettings(Long adminUserId, PayWaySettingsDto dto) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("payway_merchant_id", dto.merchantId());
        values.put("payway_api_key", dto.apiKey());
        values.put("payway_api_url", dto.apiUrl());
        values.put("payway_check_url", dto.checkUrl());
        values.put("payway_sandbox_mode", String.valueOf(dto.sandboxMode()));
        values.put("payway_enabled", String.valueOf(dto.enabled()));
        values.put("free_trial_days", String.valueOf(dto.freeTrialDays()));
        values.put("grace_period_days", String.valueOf(dto.gracePeriodDays()));
        values.put("enforce_subscription", String.valueOf(dto.enforceSubscription()));
        settingsService.update(adminUserId, values);
        return getPayWaySettings();
    }
}

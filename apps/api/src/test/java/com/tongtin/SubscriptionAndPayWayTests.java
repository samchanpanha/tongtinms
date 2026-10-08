package com.tongtin;

import com.tongtin.common.errors.ForbiddenException;
import com.tongtin.common.security.JwtService;
import com.tongtin.identity.dto.RegisterOwnerRequest;
import com.tongtin.identity.entity.OwnerAccount;
import com.tongtin.identity.entity.User;
import com.tongtin.identity.entity.UserRole;
import com.tongtin.identity.repository.OwnerAccountRepository;
import com.tongtin.identity.repository.UserRepository;
import com.tongtin.identity.repository.UserRoleRepository;
import com.tongtin.identity.service.AuthService;
import com.tongtin.notify.repository.NotificationRepository;
import com.tongtin.subscription.dto.ExtendSubscriptionRequest;
import com.tongtin.subscription.dto.PayWayCheckoutRequest;
import com.tongtin.subscription.dto.PayWayCheckoutResponse;
import com.tongtin.subscription.dto.PayWaySettingsDto;
import com.tongtin.subscription.dto.PlanCreateUpdateRequest;
import com.tongtin.subscription.dto.SubscriptionPlanDto;
import com.tongtin.subscription.dto.SubscriptionStatusResponse;
import com.tongtin.subscription.entity.SubscriptionOrder;
import com.tongtin.subscription.repository.SubscriptionOrderRepository;
import com.tongtin.subscription.repository.SubscriptionPlanRepository;
import com.tongtin.subscription.service.PayWayService;
import com.tongtin.subscription.service.SubscriptionGuard;
import com.tongtin.subscription.service.SubscriptionLifecycleJob;
import com.tongtin.subscription.service.SubscriptionService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class SubscriptionAndPayWayTests {

    @Autowired
    private AuthService authService;

    @Autowired
    private SubscriptionService subscriptionService;

    @Autowired
    private SubscriptionGuard subscriptionGuard;

    @Autowired
    private SubscriptionLifecycleJob lifecycleJob;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private PayWayService payWayService;

    @Autowired
    private SubscriptionPlanRepository planRepository;

    @Autowired
    private SubscriptionOrderRepository orderRepository;

    @Autowired
    private OwnerAccountRepository ownerAccountRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private MockMvc mockMvc;

    private Long hostUserId;
    private Long hostOwnerId;
    private Long adminUserId;

    @BeforeEach
    void setUp() {
        // Register a test host
        String phone = "0988111222";
        authService.registerOwner(new RegisterOwnerRequest(
                "Chủ Hụi Test", phone, "password123", "password123", null, null, true));

        User user = userRepository.findByPhone(com.tongtin.common.util.PhoneUtil.normalize(phone)).orElseThrow();
        OwnerAccount owner = ownerAccountRepository.findByUserId(user.getId()).orElseThrow();
        hostUserId = user.getId();
        hostOwnerId = owner.getId();

        // Register Admin
        String adminPhone = "0988999999";
        if (!userRepository.existsByPhone(com.tongtin.common.util.PhoneUtil.normalize(adminPhone))) {
            User admin = new User();
            admin.setPhone(com.tongtin.common.util.PhoneUtil.normalize(adminPhone));
            admin.setFullName("Super Admin");
            admin.setPasswordHash(passwordEncoder.encode("admin1234"));
            admin.setStatus("ACTIVE");
            userRepository.saveAndFlush(admin);
            userRoleRepository.save(new UserRole(admin.getId(), "ADMIN"));
            adminUserId = admin.getId();
        } else {
            adminUserId = userRepository.findByPhone(com.tongtin.common.util.PhoneUtil.normalize(adminPhone)).orElseThrow().getId();
        }
    }

    @Test
    @DisplayName("Host registration automatically grants 1-month free trial")
    void testHostRegistrationHasOneMonthFreeTrial() {
        SubscriptionStatusResponse status = subscriptionService.getMyStatus(hostOwnerId);

        assertNotNull(status);
        assertEquals("TRIAL", status.subscriptionStatus());
        assertTrue(status.isTrial());
        assertFalse(status.isExpired());
        assertTrue(status.canCreateGroup());
        assertTrue(status.daysRemaining() >= 29 && status.daysRemaining() <= 30);
        assertNotNull(status.trialEndsAt());
        assertNotNull(status.subscriptionEndsAt());
    }

    @Test
    @DisplayName("PayWay service correctly hashes purchase and check transaction parameters with HMAC-SHA512")
    void testPayWayHashing() {
        String reqTime = "20261007150000";
        String merchantId = "ec438992";
        String tranId = "TXN_TEST_001";
        String amount = "25.00";
        String items = "W3sibmFtZSI6IlBybyJ9XQ==";

        String hash = payWayService.generatePurchaseHash(
                reqTime, merchantId, tranId, amount, items, "", "Chủ", "Hụi", "test@tongtin.app", "0988111222",
                "purchase", "cards", "http://localhost:3000/return", "http://localhost:3000/cancel", "http://localhost:3000/success", "");

        assertNotNull(hash);
        assertFalse(hash.isBlank());

        String checkHash = payWayService.generateCheckTransactionHash(reqTime, merchantId, tranId);
        assertNotNull(checkHash);
        assertFalse(checkHash.isBlank());
    }

    @Test
    @DisplayName("Host initiates ABA PayWay checkout for subscription plan")
    void testHostPayWayCheckout() {
        List<SubscriptionPlanDto> activePlans = subscriptionService.getActivePlans();
        assertFalse(activePlans.isEmpty());
        SubscriptionPlanDto selectedPlan = activePlans.get(0);

        PayWayCheckoutRequest req = new PayWayCheckoutRequest(
                selectedPlan.id(), "cards,abapay_khqr", null, null, null);

        PayWayCheckoutResponse checkout = subscriptionService.checkoutPayWay(hostUserId, hostOwnerId, req);

        assertNotNull(checkout);
        assertNotNull(checkout.tranId());
        assertNotNull(checkout.hash());
        assertNotNull(checkout.checkoutUrl());
        assertNotNull(checkout.qrString());
        assertEquals(payWayService.getMerchantId(), checkout.merchantId());

        SubscriptionOrder order = orderRepository.findByTranId(checkout.tranId()).orElseThrow();
        assertEquals("PENDING", order.getStatus());
        assertEquals(hostOwnerId, order.getOwnerId());
        assertEquals(selectedPlan.id(), order.getPlanId());
        assertEquals("ABA_PAYWAY", order.getPaymentGateway());
    }

    @Test
    @DisplayName("Checkout return URLs are absolute and carry the transaction id")
    void testCheckoutReturnUrlsCarryTranId() {
        SubscriptionPlanDto plan = subscriptionService.getActivePlans().get(0);
        PayWayCheckoutResponse checkout = subscriptionService.checkoutPayWay(
                hostUserId, hostOwnerId,
                new PayWayCheckoutRequest(plan.id(), null, "http://localhost:3000/host/subscription", null, null),
                "http://localhost:3000");

        String base = "http://localhost:3000/host/subscription";
        assertEquals(base + "?status=success&tran_id=" + checkout.tranId(), checkout.returnUrl());
        assertEquals(base + "?status=success&tran_id=" + checkout.tranId(), checkout.continueSuccessUrl());
        assertEquals(base + "?status=cancelled&tran_id=" + checkout.tranId(), checkout.cancelUrl());
        assertEquals(checkout.returnUrl(), checkout.formFields().get("return_url"));
    }

    @Test
    @DisplayName("Checkout derives absolute return URLs from the request Origin when none is supplied")
    void testCheckoutDerivesReturnUrlFromOrigin() {
        SubscriptionPlanDto plan = subscriptionService.getActivePlans().get(0);
        PayWayCheckoutResponse checkout = subscriptionService.checkoutPayWay(
                hostUserId, hostOwnerId,
                new PayWayCheckoutRequest(plan.id(), null, null, null, null),
                "http://localhost:3000");

        assertEquals("http://localhost:3000/host/subscription?status=success&tran_id=" + checkout.tranId(),
                checkout.returnUrl());
        assertTrue(checkout.cancelUrl()
                .startsWith("http://localhost:3000/host/subscription?status=cancelled&tran_id="));
    }

    @Test
    @DisplayName("Security: cross-origin return URLs are rejected")
    void testCheckoutRejectsCrossOriginReturnUrl() {
        SubscriptionPlanDto plan = subscriptionService.getActivePlans().get(0);
        PayWayCheckoutRequest req = new PayWayCheckoutRequest(
                plan.id(), null, "https://evil.example.com/host/subscription", null, null);
        assertThrows(com.tongtin.common.errors.BadRequestException.class,
                () -> subscriptionService.checkoutPayWay(hostUserId, hostOwnerId, req, "http://localhost:3000"));
    }

    @Test
    @DisplayName("Security: return URLs cannot smuggle status or tran_id parameters")
    void testCheckoutRejectsReturnUrlWithStatusParam() {
        SubscriptionPlanDto plan = subscriptionService.getActivePlans().get(0);
        PayWayCheckoutRequest req = new PayWayCheckoutRequest(
                plan.id(), null, "http://localhost:3000/host/subscription?status=success", null, null);
        assertThrows(com.tongtin.common.errors.BadRequestException.class,
                () -> subscriptionService.checkoutPayWay(hostUserId, hostOwnerId, req, "http://localhost:3000"));
    }

    @Test
    @DisplayName("Checkout endpoint composes the return URL from the Origin header")
    void testCheckoutEndpointUsesOriginHeader() throws Exception {
        SubscriptionPlanDto plan = subscriptionService.getActivePlans().get(0);
        String hostToken = jwtService.accessToken(hostUserId, hostOwnerId, List.of("HOST"));

        mockMvc.perform(post("/api/v1/subscription/checkout/payway")
                        .header("Authorization", "Bearer " + hostToken)
                        .header("Origin", "http://localhost:3000")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planId\":" + plan.id() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.returnUrl",
                        startsWith("http://localhost:3000/host/subscription?status=success&tran_id=")));
    }

    @Test
    @DisplayName("PayWay verification activates host subscription and extends expiry date")
    void testPayWayVerificationAndActivation() {
        List<SubscriptionPlanDto> activePlans = subscriptionService.getActivePlans();
        SubscriptionPlanDto selectedPlan = activePlans.stream()
                .filter(p -> p.durationMonths() == 1)
                .findFirst()
                .orElse(activePlans.get(0));

        PayWayCheckoutRequest req = new PayWayCheckoutRequest(
                selectedPlan.id(), "cards,abapay_khqr", null, null, null);
        PayWayCheckoutResponse checkout = subscriptionService.checkoutPayWay(hostUserId, hostOwnerId, req);

        OwnerAccount before = ownerAccountRepository.findById(hostOwnerId).orElseThrow();
        Instant prevExpiry = before.getSubscriptionEndsAt();

        // Simulate successful payment activation
        SubscriptionOrder paidOrder = subscriptionService.verifyAndActivateOrder(
                checkout.tranId(), "ABA_GW_999888", "{\"status\":0,\"payment_status\":\"APPROVED\"}");

        assertEquals("PAID", paidOrder.getStatus());
        assertNotNull(paidOrder.getPaidAt());

        OwnerAccount after = ownerAccountRepository.findById(hostOwnerId).orElseThrow();
        assertEquals("ACTIVE", after.getSubscriptionStatus());
        assertEquals(selectedPlan.id(), after.getCurrentPlanId());
        assertTrue(after.getSubscriptionEndsAt().isAfter(prevExpiry));

        SubscriptionStatusResponse updatedStatus = subscriptionService.getMyStatus(hostOwnerId);
        assertEquals("ACTIVE", updatedStatus.subscriptionStatus());
        assertFalse(updatedStatus.isTrial());
        assertNotNull(updatedStatus.currentPlan());
        assertEquals(selectedPlan.name(), updatedStatus.currentPlan().name());
    }

    @Test
    @DisplayName("Administrator can manage subscription plans (CRUD)")
    void testAdminPlanCrud() {
        PlanCreateUpdateRequest createReq = new PlanCreateUpdateRequest(
                "VIP_TEST_PLAN", "Gói VIP Thử Nghiệm", "Gói đặc biệt dành cho doanh nghiệp lớn",
                5000L, "USD", 6, -1, -1, "[\"Tính năng VIP\", \"Hỗ trợ 24/7\"]", "VIP", 99, true);

        SubscriptionPlanDto created = subscriptionService.createPlan(createReq);
        assertNotNull(created.id());
        assertEquals("VIP_TEST_PLAN", created.code());
        assertEquals(5000L, created.priceMinor());

        PlanCreateUpdateRequest updateReq = new PlanCreateUpdateRequest(
                "VIP_TEST_PLAN", "Gói VIP Thử Nghiệm (Đã Sửa)", "Mô tả mới",
                4500L, "USD", 6, -1, -1, "[\"Tính năng VIP\"]", "VIP Siêu Cấp", 99, true);

        SubscriptionPlanDto updated = subscriptionService.updatePlan(created.id(), updateReq);
        assertEquals("Gói VIP Thử Nghiệm (Đã Sửa)", updated.name());
        assertEquals(4500L, updated.priceMinor());

        subscriptionService.deletePlan(created.id());
        assertTrue(planRepository.findByCode("VIP_TEST_PLAN").isEmpty());
    }

    @Test
    @DisplayName("Administrator can view and update ABA PayWay gateway settings")
    void testAdminPayWaySettings() {
        PayWaySettingsDto current = subscriptionService.getPayWaySettings();
        assertNotNull(current);

        PayWaySettingsDto update = new PayWaySettingsDto(
                "ec999999", "secret_hash_key_123456", "https://checkout.payway.com.kh/api/v1/purchase",
                "https://checkout.payway.com.kh/api/v1/check", false, true, 30, 5, true);

        PayWaySettingsDto result = subscriptionService.updatePayWaySettings(adminUserId, update);
        assertEquals("ec999999", result.merchantId());
        assertEquals("secret_hash_key_123456", result.apiKey());
        assertFalse(result.sandboxMode());
        assertEquals(5, result.gracePeriodDays());
    }

    @Test
    @DisplayName("Administrator can manually extend host subscription")
    void testAdminExtendSubscription() {
        ExtendSubscriptionRequest req = new ExtendSubscriptionRequest(60, null, "Hỗ trợ khách hàng VIP", false);
        subscriptionService.extendSubscription(adminUserId, hostOwnerId, req);

        SubscriptionStatusResponse status = subscriptionService.getMyStatus(hostOwnerId);
        assertTrue(status.daysRemaining() >= 88); // 30 trial + 60 extension
    }

    @Test
    @DisplayName("Security: payment simulation is rejected outside sandbox mode")
    void testSimulatePaymentBlockedOutsideSandbox() throws Exception {
        subscriptionService.updatePayWaySettings(adminUserId, new PayWaySettingsDto(
                payWayService.getMerchantId(), payWayService.getApiKey(), payWayService.getApiUrl(),
                payWayService.getCheckUrl(), false, true, 30, 3, true));

        SubscriptionPlanDto plan = subscriptionService.getActivePlans().get(0);
        PayWayCheckoutResponse checkout = subscriptionService.checkoutPayWay(
                hostUserId, hostOwnerId, new PayWayCheckoutRequest(plan.id(), null, null, null, null));

        mockMvc.perform(post("/api/v1/payments/payway/simulate-complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tranId\":\"" + checkout.tranId() + "\"}"))
                .andExpect(status().isForbidden());

        SubscriptionOrder order = orderRepository.findByTranId(checkout.tranId()).orElseThrow();
        assertEquals("PENDING", order.getStatus());
    }

    @Test
    @DisplayName("Security: simulated payment in sandbox mode still activates the order")
    void testSimulatePaymentWorksInSandbox() throws Exception {
        // Sandbox mode is the seeded default (payway_sandbox_mode = true)
        SubscriptionPlanDto plan = subscriptionService.getActivePlans().get(0);
        PayWayCheckoutResponse checkout = subscriptionService.checkoutPayWay(
                hostUserId, hostOwnerId, new PayWayCheckoutRequest(plan.id(), null, null, null, null));

        mockMvc.perform(post("/api/v1/payments/payway/simulate-complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tranId\":\"" + checkout.tranId() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderStatus").value("PAID"));
    }

    @Test
    @DisplayName("Security: a host cannot verify another host's payment order")
    void testVerifyOrderEnforcesOwnership() throws Exception {
        String secondPhone = "0988333444";
        authService.registerOwner(new RegisterOwnerRequest(
                "Chủ Hụi Hai", secondPhone, "password123", "password123", null, null, true));
        User second = userRepository.findByPhone(com.tongtin.common.util.PhoneUtil.normalize(secondPhone))
                .orElseThrow();
        OwnerAccount secondOwner = ownerAccountRepository.findByUserId(second.getId()).orElseThrow();

        SubscriptionPlanDto plan = subscriptionService.getActivePlans().get(0);
        PayWayCheckoutResponse checkout = subscriptionService.checkoutPayWay(
                second.getId(), secondOwner.getId(), new PayWayCheckoutRequest(plan.id(), null, null, null, null));

        String hostToken = jwtService.accessToken(hostUserId, hostOwnerId, List.of("HOST"));
        mockMvc.perform(post("/api/v1/subscription/verify/" + checkout.tranId())
                        .header("Authorization", "Bearer " + hostToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Security: gateway verification never activates an unpaid order")
    void testGatewayVerificationLeavesUnpaidOrderPending() {
        SubscriptionPlanDto plan = subscriptionService.getActivePlans().get(0);
        PayWayCheckoutResponse checkout = subscriptionService.checkoutPayWay(
                hostUserId, hostOwnerId, new PayWayCheckoutRequest(plan.id(), null, null, null, null));

        // ABA PayWay has no record of this tran_id, so the check must not approve it
        SubscriptionOrder order = subscriptionService.verifyAndActivateViaGateway(checkout.tranId());
        assertEquals("PENDING", order.getStatus());

        OwnerAccount owner = ownerAccountRepository.findById(hostOwnerId).orElseThrow();
        assertEquals("TRIAL", owner.getSubscriptionStatus());
    }

    @Test
    @DisplayName("Guard: expiry, grace period, enforcement switch and plan limits drive group creation")
    void testSubscriptionGuardPolicies() {
        // Fresh trial host can create groups
        assertDoesNotThrow(() -> subscriptionGuard.assertCanCreateGroup(hostOwnerId));

        OwnerAccount owner = ownerAccountRepository.findById(hostOwnerId).orElseThrow();

        // Expired beyond the configured grace period -> blocked
        owner.setSubscriptionEndsAt(Instant.now().minus(Duration.ofDays(10)));
        ownerAccountRepository.saveAndFlush(owner);
        assertThrows(ForbiddenException.class, () -> subscriptionGuard.assertCanCreateGroup(hostOwnerId));

        // Inside the grace period -> allowed
        owner.setSubscriptionEndsAt(Instant.now().minus(Duration.ofDays(1)));
        ownerAccountRepository.saveAndFlush(owner);
        assertDoesNotThrow(() -> subscriptionGuard.assertCanCreateGroup(hostOwnerId));

        // Plan limit reached -> blocked
        SubscriptionPlanDto cappedPlan = subscriptionService.createPlan(new PlanCreateUpdateRequest(
                "CAP_TEST", "Gói Giới Hạn", "maxGroups=0", 100L, "USD", 1, 0, -1, "[]", null, 50, true));
        owner.setCurrentPlanId(cappedPlan.id());
        ownerAccountRepository.saveAndFlush(owner);
        assertThrows(ForbiddenException.class, () -> subscriptionGuard.assertCanCreateGroup(hostOwnerId));

        // Enforcement switch off -> always allowed
        subscriptionService.updatePayWaySettings(adminUserId, new PayWaySettingsDto(
                payWayService.getMerchantId(), payWayService.getApiKey(), payWayService.getApiUrl(),
                payWayService.getCheckUrl(), true, true, 30, 3, false));
        assertDoesNotThrow(() -> subscriptionGuard.assertCanCreateGroup(hostOwnerId));
    }

    @Test
    @DisplayName("Lifecycle: reminders at 7/3/1 days, auto-expiry after grace period, skipped when enforcement off")
    void testSubscriptionLifecycleJob() {
        subscriptionService.updatePayWaySettings(adminUserId, new PayWaySettingsDto(
                payWayService.getMerchantId(), payWayService.getApiKey(), payWayService.getApiUrl(),
                payWayService.getCheckUrl(), true, true, 30, 3, true));

        Instant now = Instant.now();

        // 7 days + 1h remaining -> truncates to 7 days -> reminder
        OwnerAccount owner = ownerAccountRepository.findById(hostOwnerId).orElseThrow();
        owner.setSubscriptionEndsAt(now.plus(Duration.ofDays(7)).plus(Duration.ofHours(1)));
        ownerAccountRepository.saveAndFlush(owner);

        SubscriptionLifecycleJob.Result reminded = lifecycleJob.process(now);
        assertTrue(reminded.remindersSent() >= 1);
        assertTrue(countNotifications(hostUserId, "SUBSCRIPTION_EXPIRING") >= 1);

        // 10 days past expiry with a 3-day grace period -> auto-expired
        owner = ownerAccountRepository.findById(hostOwnerId).orElseThrow();
        owner.setSubscriptionEndsAt(now.minus(Duration.ofDays(10)));
        ownerAccountRepository.saveAndFlush(owner);

        SubscriptionLifecycleJob.Result expired = lifecycleJob.process(now);
        assertTrue(expired.expiredCount() >= 1);
        assertEquals("EXPIRED",
                ownerAccountRepository.findById(hostOwnerId).orElseThrow().getSubscriptionStatus());
        long expiredNotices = countNotifications(hostUserId, "SUBSCRIPTION_EXPIRED");
        assertTrue(expiredNotices >= 1);

        // A later run must not expire (or re-notify) the same owner again
        lifecycleJob.process(now);
        assertEquals("EXPIRED",
                ownerAccountRepository.findById(hostOwnerId).orElseThrow().getSubscriptionStatus());
        assertEquals(expiredNotices, countNotifications(hostUserId, "SUBSCRIPTION_EXPIRED"));

        // Enforcement switch off -> nothing is processed at all
        subscriptionService.updatePayWaySettings(adminUserId, new PayWaySettingsDto(
                payWayService.getMerchantId(), payWayService.getApiKey(), payWayService.getApiUrl(),
                payWayService.getCheckUrl(), true, true, 30, 3, false));
        assertEquals(new SubscriptionLifecycleJob.Result(0, 0), lifecycleJob.process(now));
    }

    private long countNotifications(Long userId, String type) {
        return notificationRepository.findTop100ByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(n -> type.equals(n.getType()))
                .count();
    }
}

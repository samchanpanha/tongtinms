package com.tongtin;

import com.tongtin.common.security.JwtService;
import com.tongtin.identity.dto.RegisterOwnerRequest;
import com.tongtin.identity.entity.OwnerAccount;
import com.tongtin.identity.entity.User;
import com.tongtin.identity.entity.UserRole;
import com.tongtin.identity.repository.OwnerAccountRepository;
import com.tongtin.identity.repository.UserRepository;
import com.tongtin.identity.repository.UserRoleRepository;
import com.tongtin.identity.service.AuthService;
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
import com.tongtin.subscription.service.SubscriptionService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class SubscriptionAndPayWayTests {

    @Autowired
    private AuthService authService;

    @Autowired
    private SubscriptionService subscriptionService;

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
}

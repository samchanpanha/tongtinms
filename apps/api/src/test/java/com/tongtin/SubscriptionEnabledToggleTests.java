package com.tongtin;

import com.tongtin.common.errors.BadRequestException;
import com.tongtin.common.errors.ForbiddenException;
import com.tongtin.identity.dto.RegisterOwnerRequest;
import com.tongtin.identity.entity.OwnerAccount;
import com.tongtin.identity.entity.User;
import com.tongtin.identity.entity.UserRole;
import com.tongtin.identity.repository.OwnerAccountRepository;
import com.tongtin.identity.repository.UserRepository;
import com.tongtin.identity.repository.UserRoleRepository;
import com.tongtin.identity.service.AuthService;
import com.tongtin.settings.service.SettingsService;
import com.tongtin.subscription.dto.PayWayCheckoutRequest;
import com.tongtin.subscription.dto.PayWaySettingsDto;
import com.tongtin.subscription.dto.SubscriptionPlanDto;
import com.tongtin.subscription.dto.SubscriptionStatusResponse;
import com.tongtin.subscription.service.PayWayService;
import com.tongtin.subscription.service.SubscriptionGuard;
import com.tongtin.subscription.service.SubscriptionLifecycleJob;
import com.tongtin.subscription.service.SubscriptionService;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Hermetic tests for the subscription_enabled master switch (admin setting that
 * disables/enables the whole subscription subsystem without a code deploy):
 *   - guard allows every host to create groups when OFF (never blocks expiry/limits)
 *   - GET my-status reports ACTIVE + canCreateGroup even for an expired account
 *   - checkout is refused while OFF
 *   - the lifecycle job is parked while OFF (no reminders, no auto-expiry)
 *   - the switch is toggled and round-tripped through the admin PayWay settings
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class SubscriptionEnabledToggleTests {

    @Autowired
    private AuthService authService;

    @Autowired
    private SubscriptionService subscriptionService;

    @Autowired
    private SubscriptionGuard subscriptionGuard;

    @Autowired
    private SubscriptionLifecycleJob lifecycleJob;

    @Autowired
    private SettingsService settingsService;

    @Autowired
    private OwnerAccountRepository ownerAccountRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PayWayService payWayService;

    private Long hostUserId;
    private Long hostOwnerId;
    private Long adminUserId;

    @BeforeEach
    void setUp() {
        String phone = "0988555111";
        authService.registerOwner(new RegisterOwnerRequest(
                "Chủ Hụi Toggle", phone, "password123", "password123", null, null, true));

        User user = userRepository.findByPhone(com.tongtin.common.util.PhoneUtil.normalize(phone)).orElseThrow();
        OwnerAccount owner = ownerAccountRepository.findByUserId(user.getId()).orElseThrow();
        hostUserId = user.getId();
        hostOwnerId = owner.getId();

        String adminPhone = "0988999777";
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
            adminUserId = userRepository.findByPhone(
                    com.tongtin.common.util.PhoneUtil.normalize(adminPhone)).orElseThrow().getId();
        }
    }

    private void setSubscriptionEnabled(boolean enabled) {
        settingsService.update(adminUserId, Map.of("subscription_enabled", String.valueOf(enabled)));
    }

    @Test
    @DisplayName("Default configuration has the subscription subsystem enabled")
    void testDefaultIsEnabled() {
        assertTrue(subscriptionGuard.isSubscriptionEnabled());
        SubscriptionStatusResponse status = subscriptionService.getMyStatus(hostOwnerId);
        assertTrue(status.subscriptionEnabled());
        assertTrue(settingsService.getBoolean("subscription_enabled", false));
    }

    @Test
    @DisplayName("Disabled switch: an expired host is never blocked from creating a group")
    void testDisabledDoesNotBlockExpiredHost() {
        OwnerAccount owner = ownerAccountRepository.findById(hostOwnerId).orElseThrow();
        owner.setSubscriptionEndsAt(Instant.now().minus(Duration.ofDays(10)));
        owner.setSubscriptionStatus("EXPIRED");
        ownerAccountRepository.saveAndFlush(owner);

        setSubscriptionEnabled(true);
        assertThrows(ForbiddenException.class, () -> subscriptionGuard.assertCanCreateGroup(hostOwnerId));

        setSubscriptionEnabled(false);
        assertDoesNotThrow(() -> subscriptionGuard.assertCanCreateGroup(hostOwnerId));
    }

    @Test
    @DisplayName("Disabled switch: my-status reports ACTIVE, never expired, and canCreateGroup")
    void testDisabledReportsActiveStatus() {
        OwnerAccount owner = ownerAccountRepository.findById(hostOwnerId).orElseThrow();
        owner.setSubscriptionEndsAt(Instant.now().minus(Duration.ofDays(10)));
        ownerAccountRepository.saveAndFlush(owner);

        setSubscriptionEnabled(false);
        SubscriptionStatusResponse status = subscriptionService.getMyStatus(hostOwnerId);
        assertFalse(status.subscriptionEnabled());
        assertEquals("ACTIVE", status.subscriptionStatus());
        assertFalse(status.isExpired());
        assertFalse(status.isGracePeriod());
        assertTrue(status.canCreateGroup());

        setSubscriptionEnabled(true);
        status = subscriptionService.getMyStatus(hostOwnerId);
        assertTrue(status.subscriptionEnabled());
        assertTrue(status.isExpired());
        assertFalse(status.canCreateGroup());
    }

    @Test
    @DisplayName("Disabled switch: stock subscription checkout is refused")
    void testDisabledRefusesCheckout() {
        SubscriptionPlanDto plan = subscriptionService.getActivePlans().get(0);
        setSubscriptionEnabled(false);
        assertThrows(BadRequestException.class, () -> subscriptionService.checkoutPayWay(
                hostUserId, hostOwnerId, new PayWayCheckoutRequest(plan.id(), null, null, null, null)));

        setSubscriptionEnabled(true);
        assertDoesNotThrow(() -> subscriptionService.checkoutPayWay(
                hostUserId, hostOwnerId, new PayWayCheckoutRequest(plan.id(), null, null, null, null)));
    }

    @Test
    @DisplayName("Disabled switch: lifecycle job is parked and auto-expiry is suspended")
    void testDisabledParksLifecycleJob() {
        OwnerAccount owner = ownerAccountRepository.findById(hostOwnerId).orElseThrow();
        owner.setSubscriptionEndsAt(Instant.now().minus(Duration.ofDays(10)));
        ownerAccountRepository.saveAndFlush(owner);
        Instant now = Instant.now();

        setSubscriptionEnabled(false);
        SubscriptionLifecycleJob.Result parked = lifecycleJob.process(now);
        assertEquals(0, parked.remindersSent());
        assertEquals(0, parked.expiredCount());
        assertNotEquals("EXPIRED",
                ownerAccountRepository.findById(hostOwnerId).orElseThrow().getSubscriptionStatus());

        setSubscriptionEnabled(true);
        SubscriptionLifecycleJob.Result active = lifecycleJob.process(now);
        assertTrue(active.expiredCount() >= 1);
        assertEquals("EXPIRED",
                ownerAccountRepository.findById(hostOwnerId).orElseThrow().getSubscriptionStatus());
    }

    @Test
    @DisplayName("Admin can toggle subscription_enabled through the PayWay settings round-trip")
    void testAdminRoundTripToggle() {
        PayWaySettingsDto update = new PayWaySettingsDto(
                payWayService.getMerchantId(), payWayService.getApiKey(), payWayService.getApiUrl(),
                payWayService.getCheckUrl(), true, true, 30, 3, true, false);

        PayWaySettingsDto result = subscriptionService.updatePayWaySettings(adminUserId, update);
        assertFalse(result.subscriptionEnabled());
        assertFalse(subscriptionGuard.isSubscriptionEnabled());
        assertFalse(settingsService.getBoolean("subscription_enabled", true));

        Map<String, String> middleware = new LinkedHashMap<>();
        middleware.put("subscription_enabled", "true");
        settingsService.update(adminUserId, middleware);
        assertTrue(subscriptionService.getPayWaySettings().subscriptionEnabled());
        assertTrue(subscriptionGuard.isSubscriptionEnabled());
    }
}
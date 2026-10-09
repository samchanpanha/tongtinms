package com.tongtin;

import com.tongtin.common.errors.BadRequestException;
import com.tongtin.common.errors.ForbiddenException;
import com.tongtin.common.security.JwtService;
import com.tongtin.common.util.PhoneUtil;
import com.tongtin.groups.dto.GroupCreateRequest;
import com.tongtin.groups.dto.GroupResponse;
import com.tongtin.groups.service.GroupService;
import com.tongtin.identity.dto.AuthResponse;
import com.tongtin.identity.dto.LoginRequest;
import com.tongtin.identity.dto.RegisterOwnerRequest;
import com.tongtin.identity.entity.OwnerAccount;
import com.tongtin.identity.entity.User;
import com.tongtin.identity.entity.UserRole;
import com.tongtin.identity.repository.OwnerAccountRepository;
import com.tongtin.identity.repository.UserRepository;
import com.tongtin.identity.repository.UserRoleRepository;
import com.tongtin.identity.service.AuthService;
import com.tongtin.notify.entity.Notification;
import com.tongtin.notify.repository.NotificationRepository;
import com.tongtin.settings.dto.SettingCategoryDto;
import com.tongtin.settings.dto.SettingDto;
import com.tongtin.settings.dto.SettingsViewDto;
import com.tongtin.settings.service.SettingsService;
import com.tongtin.subscription.service.SubscriptionGuard;
import com.tongtin.subscription.service.SubscriptionLifecycleJob;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class AdminSettingsTests {

    @Autowired
    private SettingsService settingsService;

    @Autowired
    private SubscriptionGuard subscriptionGuard;

    @Autowired
    private SubscriptionLifecycleJob lifecycleJob;

    @Autowired
    private GroupService groupService;

    @Autowired
    private AuthService authService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private OwnerAccountRepository ownerAccountRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private MockMvc mockMvc;

    private Long hostUserId;
    private Long hostOwnerId;
    private Long adminUserId;

    @BeforeEach
    void setUp() {
        String phone = "0988111222";
        authService.registerOwner(new RegisterOwnerRequest(
                "Chủ Hụi Test", phone, "password123", "password123", null, null, true));

        User user = userRepository.findByPhone(PhoneUtil.normalize(phone)).orElseThrow();
        OwnerAccount owner = ownerAccountRepository.findByUserId(user.getId()).orElseThrow();
        hostUserId = user.getId();
        hostOwnerId = owner.getId();

        String adminPhone = "0988999999";
        User admin = userRepository.findByPhone(PhoneUtil.normalize(adminPhone)).orElse(null);
        if (admin == null) {
            admin = new User();
            admin.setPhone(PhoneUtil.normalize(adminPhone));
            admin.setFullName("Super Admin");
            admin.setPasswordHash(passwordEncoder.encode("admin1234"));
            admin.setStatus("ACTIVE");
            userRepository.saveAndFlush(admin);
            userRoleRepository.save(new UserRole(admin.getId(), "ADMIN"));
        }
        adminUserId = admin.getId();
    }

    @Test
    @DisplayName("Settings catalog exposes every module category and masks SECRET values")
    void testCatalogGroupedAndSecretsMasked() {
        SettingsViewDto view = settingsService.getView();

        assertEquals(5, view.categories().size());
        assertEquals(List.of("PAYMENT", "SUBSCRIPTION", "GROUPS", "SECURITY", "TELEGRAM"),
                view.categories().stream().map(SettingCategoryDto::code).toList());

        SettingDto apiKey = findSetting(view, "payway_api_key");
        assertEquals("SECRET", apiKey.type());
        assertNull(apiKey.value());
        assertNull(apiKey.defaultValue());
        assertTrue(apiKey.configured());

        SettingDto trialDays = findSetting(view, "free_trial_days");
        assertEquals("INT", trialDays.type());
        assertEquals("30", trialDays.value());
        assertEquals("30", trialDays.defaultValue());
        assertEquals(Integer.valueOf(1), trialDays.min());
        assertEquals(Integer.valueOf(365), trialDays.max());
        assertNotNull(trialDays.updatedAt());

        SettingDto reminderDays = findSetting(view, "subscription_reminder_days");
        assertEquals("INT_LIST", reminderDays.type());
        assertEquals("7,3,1", reminderDays.value());
        assertNull(reminderDays.updatedAt());
    }

    @Test
    @DisplayName("Settings API is admin-only and never leaks the stored secret")
    void testSettingsApiRequiresAdmin() throws Exception {
        String hostToken = jwtService.accessToken(hostUserId, hostOwnerId, List.of("HOST"));
        String adminToken = jwtService.accessToken(adminUserId, null, List.of("ADMIN"));

        mockMvc.perform(get("/api/v1/admin/settings"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/admin/settings")
                        .header("Authorization", "Bearer " + hostToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/admin/settings")
                        .header("Authorization", "Bearer " + hostToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"values\":{\"grace_period_days\":\"7\"}}"))
                .andExpect(status().isForbidden());

        String body = mockMvc.perform(get("/api/v1/admin/settings")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories.length()").value(5))
                .andReturn().getResponse().getContentAsString();

        assertFalse(body.contains("4c05336bf1f621375d86242aebe3daee7fa5a1c3"));
    }

    @Test
    @DisplayName("Invalid setting payloads are rejected and surfaced as HTTP 400")
    void testUpdateValidationRejectsBadPayloads() throws Exception {
        assertThrows(BadRequestException.class, () -> settingsService.update(adminUserId, Map.of()));
        assertThrows(BadRequestException.class,
                () -> settingsService.update(adminUserId, Map.of("no_such_key", "x")));
        assertThrows(BadRequestException.class,
                () -> settingsService.update(adminUserId, Map.of("payway_sandbox_mode", "yes")));
        assertThrows(BadRequestException.class,
                () -> settingsService.update(adminUserId, Map.of("grace_period_days", "99")));
        assertThrows(BadRequestException.class,
                () -> settingsService.update(adminUserId, Map.of("grace_period_days", "abc")));
        assertThrows(BadRequestException.class,
                () -> settingsService.update(adminUserId, Map.of("subscription_reminder_days", "7,x")));
        assertThrows(BadRequestException.class,
                () -> settingsService.update(adminUserId, Map.of("payway_api_url", "ftp://payway")));
        assertThrows(BadRequestException.class,
                () -> settingsService.update(adminUserId, Map.of("free_trial_days", "   ")));

        String adminToken = jwtService.accessToken(adminUserId, null, List.of("ADMIN"));
        mockMvc.perform(put("/api/v1/admin/settings")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"values\":{\"payway_api_url\":\"ftp://bad\"}}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Updates are normalized and blank secrets keep the stored value")
    void testUpdateNormalizesAndBlankSecretKeepsValue() {
        String storedSecret = settingsService.getString("payway_api_key", null);
        assertNotNull(storedSecret);

        SettingsViewDto updated = settingsService.update(adminUserId, Map.of(
                "grace_period_days", "5",
                "payway_sandbox_mode", "FALSE",
                "subscription_reminder_days", "1,3,3",
                "payway_api_url", "https://sandbox.payway.com.kh/custom/purchase"));

        assertEquals("5", findSetting(updated, "grace_period_days").value());
        assertEquals("false", findSetting(updated, "payway_sandbox_mode").value());
        assertEquals("3,1", findSetting(updated, "subscription_reminder_days").value());

        assertEquals(5, settingsService.getInt("grace_period_days", 3));
        assertFalse(settingsService.getBoolean("payway_sandbox_mode", true));
        assertEquals(List.of(3, 1), settingsService.getIntList("subscription_reminder_days", List.of(7, 3, 1)));

        settingsService.update(adminUserId, Map.of("payway_api_key", "   "));
        assertEquals(storedSecret, settingsService.getString("payway_api_key", null));

        settingsService.update(adminUserId, Map.of("payway_api_key", "new-secret-123"));
        assertEquals("new-secret-123", settingsService.getString("payway_api_key", null));

        SettingDto apiKey = findSetting(settingsService.getView(), "payway_api_key");
        assertNull(apiKey.value());
        assertTrue(apiKey.configured());
    }

    @Test
    @DisplayName("grace_period_days setting drives the subscription guard")
    void testGracePeriodSettingDrivesGuard() {
        settingsService.update(adminUserId, Map.of("grace_period_days", "5", "enforce_subscription", "true"));

        OwnerAccount owner = ownerAccountRepository.findById(hostOwnerId).orElseThrow();
        Instant now = Instant.now();

        owner.setSubscriptionEndsAt(now.minus(Duration.ofDays(4)));
        ownerAccountRepository.saveAndFlush(owner);
        assertDoesNotThrow(() -> subscriptionGuard.assertCanCreateGroup(hostOwnerId));

        owner.setSubscriptionEndsAt(now.minus(Duration.ofDays(6)));
        ownerAccountRepository.saveAndFlush(owner);
        assertThrows(ForbiddenException.class, () -> subscriptionGuard.assertCanCreateGroup(hostOwnerId));
    }

    @Test
    @DisplayName("free_trial_days setting drives the trial length of new registrations")
    void testFreeTrialDaysSettingDrivesRegistration() {
        settingsService.update(adminUserId, Map.of("free_trial_days", "10"));

        Instant before = Instant.now();
        String phone = "0988333444";
        authService.registerOwner(new RegisterOwnerRequest(
                "Chủ Hụi Mới", phone, "password123", "password123", null, null, true));

        User user = userRepository.findByPhone(PhoneUtil.normalize(phone)).orElseThrow();
        OwnerAccount owner = ownerAccountRepository.findByUserId(user.getId()).orElseThrow();

        Instant expected = before.plus(Duration.ofDays(10));
        assertTrue(owner.getSubscriptionEndsAt().isAfter(expected.minus(Duration.ofMinutes(5))));
        assertTrue(owner.getSubscriptionEndsAt().isBefore(expected.plus(Duration.ofMinutes(5))));
        assertEquals("TRIAL", owner.getSubscriptionStatus());
    }

    @Test
    @DisplayName("default_bid_close_offset_days applies when a group omits the offset")
    void testDefaultBidCloseOffsetDrivesGroupCreate() {
        settingsService.update(adminUserId, Map.of("default_bid_close_offset_days", "5"));

        GroupResponse implicit = groupService.create(hostOwnerId, groupRequest("Nhóm mặc định", null));
        assertEquals(5, implicit.bidCloseOffset());

        GroupResponse explicit = groupService.create(hostOwnerId, groupRequest("Nhóm nhập tay", 2));
        assertEquals(2, explicit.bidCloseOffset());
    }

    @Test
    @DisplayName("subscription_reminder_days setting drives lifecycle reminders")
    void testReminderDaysSettingDrivesLifecycleJob() {
        settingsService.update(adminUserId, Map.of("subscription_reminder_days", "2"));

        OwnerAccount owner = ownerAccountRepository.findById(hostOwnerId).orElseThrow();
        Instant now = Instant.now();
        owner.setSubscriptionEndsAt(now.plus(Duration.ofDays(2)).plus(Duration.ofHours(1)));
        ownerAccountRepository.saveAndFlush(owner);

        SubscriptionLifecycleJob.Result result = lifecycleJob.process(now);
        assertTrue(result.remindersSent() >= 1);

        List<Notification> notifications =
                notificationRepository.findTop100ByUserIdOrderByCreatedAtDesc(hostUserId);
        assertTrue(notifications.stream().anyMatch(n -> "SUBSCRIPTION_EXPIRING".equals(n.getType())));
    }

    @Test
    @DisplayName("access_token_ttl_minutes setting drives issued token lifetime")
    void testAccessTokenTtlSettingDrivesLogin() {
        settingsService.update(adminUserId, Map.of("access_token_ttl_minutes", "45"));

        AuthResponse response = authService.login(new LoginRequest("0988111222", "password123"));
        Object expiresIn = response.tokens().get("expiresIn");
        assertNotNull(expiresIn);
        assertEquals(2700L, ((Number) expiresIn).longValue());
    }

    private SettingDto findSetting(SettingsViewDto view, String key) {
        return view.categories().stream()
                .flatMap(category -> category.settings().stream())
                .filter(setting -> setting.key().equals(key))
                .findFirst()
                .orElseThrow();
    }

    private GroupCreateRequest groupRequest(String name, Integer bidCloseOffset) {
        return new GroupCreateRequest(name, "FIXED", 1_000_000L, 3, "MONTH", 3, "VND",
                null, null, null, null, null, null, null, null, null, null, null, bidCloseOffset, null);
    }
}

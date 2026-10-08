package com.tongtin;

import com.tongtin.identity.dto.RegisterOwnerRequest;
import com.tongtin.identity.entity.OwnerAccount;
import com.tongtin.identity.entity.User;
import com.tongtin.identity.repository.OwnerAccountRepository;
import com.tongtin.identity.repository.UserRepository;
import com.tongtin.identity.service.AuthService;
import com.tongtin.settings.service.SettingsService;
import com.tongtin.subscription.dto.PayWayCheckoutRequest;
import com.tongtin.subscription.dto.PayWayCheckoutResponse;
import com.tongtin.subscription.dto.SubscriptionPlanDto;
import com.tongtin.subscription.entity.PaymentEvent;
import com.tongtin.subscription.entity.SubscriptionOrder;
import com.tongtin.subscription.repository.PaymentEventRepository;
import com.tongtin.subscription.service.PaymentEventService;
import com.tongtin.subscription.service.SubscriptionService;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Step 25: append-only payment_events forensics + per-IP callback rate limit.
 * Uses distinct X-Forwarded-For IPs per test so the in-memory limiter windows
 * never leak between test methods.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class PaymentForensicsTests {

    private static final String TEST_IP_FORENSICS = "10.99.0.11";
    private static final String TEST_IP_RATE = "10.99.0.12";

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OwnerAccountRepository ownerAccountRepository;

    @Autowired
    private SubscriptionService subscriptionService;

    @Autowired
    private PaymentEventService paymentEventService;

    @Autowired
    private PaymentEventRepository paymentEventRepository;

    @Autowired
    private SettingsService settingsService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    private Long hostUserId;
    private Long hostOwnerId;

    @BeforeEach
    void setUp() {
        String phone = "0988222333";
        authService.registerOwner(new RegisterOwnerRequest(
                "Chủ Hụi Forensics", phone, "password123", "password123", null, null, true));

        User user = userRepository.findByPhone(com.tongtin.common.util.PhoneUtil.normalize(phone)).orElseThrow();
        OwnerAccount owner = ownerAccountRepository.findByUserId(user.getId()).orElseThrow();
        hostUserId = user.getId();
        hostOwnerId = owner.getId();
    }

    private String createOrder() {
        SubscriptionPlanDto plan = subscriptionService.getActivePlans().get(0);
        PayWayCheckoutResponse checkout = subscriptionService.checkoutPayWay(
                hostUserId, hostOwnerId, new PayWayCheckoutRequest(plan.id(), null, null, null, null));
        return checkout.tranId();
    }

    @Test
    @DisplayName("Callback invocation journals a CALLBACK row and the gateway exchange a CHECK row")
    void callbackJournalsCallbackAndCheckEvents() throws Exception {
        String tranId = createOrder();

        mockMvc.perform(post("/api/v1/payments/payway/callback")
                        .header("X-Forwarded-For", TEST_IP_FORENSICS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .param("tran_id", tranId)
                        .param("status", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderStatus").value("PENDING"));

        // flush + detach so DB-generated columns (created_at) are re-read
        entityManager.flush();
        entityManager.clear();

        List<PaymentEvent> events = paymentEventRepository.findByTranIdOrderByCreatedAtDesc(tranId);

        PaymentEvent callback = events.stream()
                .filter(e -> "CALLBACK".equals(e.getSource()))
                .findFirst().orElseThrow();
        assertEquals("ORDER_PENDING", callback.getOutcome());
        assertNotNull(callback.getRemoteIp());
        assertTrue(callback.getPayload().contains(tranId));
        assertNotNull(callback.getCreatedAt());

        PaymentEvent check = events.stream()
                .filter(e -> "CHECK".equals(e.getSource()))
                .findFirst().orElseThrow();
        assertEquals("NOT_APPROVED", check.getOutcome());
        assertFalse(check.getPayload().isBlank());
        assertNull(check.getRemoteIp());
    }

    @Test
    @DisplayName("Callback without tran_id returns 400 and is journaled")
    void callbackMissingTranIdIsJournaled() throws Exception {
        mockMvc.perform(post("/api/v1/payments/payway/callback")
                        .header("X-Forwarded-For", TEST_IP_FORENSICS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .param("status", "0"))
                .andExpect(status().isBadRequest());

        boolean journaled = paymentEventRepository.findAllByOrderByCreatedAtDesc().stream()
                .anyMatch(e -> "CALLBACK".equals(e.getSource())
                        && "MISSING_TRAN_ID".equals(e.getOutcome())
                        && e.getTranId() == null
                        && e.getRemoteIp() != null);
        assertTrue(journaled, "MISSING_TRAN_ID callback must be journaled");
    }

    @Test
    @DisplayName("Direct gateway verification journals the raw check-transaction result")
    void gatewayVerificationJournalsCheckEvent() {
        String tranId = createOrder();

        SubscriptionOrder order = subscriptionService.verifyAndActivateViaGateway(tranId);
        assertEquals("PENDING", order.getStatus());

        List<PaymentEvent> events = paymentEventRepository.findByTranIdOrderByCreatedAtDesc(tranId);
        PaymentEvent check = events.stream()
                .filter(e -> "CHECK".equals(e.getSource()))
                .findFirst().orElseThrow();
        assertEquals("NOT_APPROVED", check.getOutcome());
        assertFalse(check.getPayload().isBlank());
    }

    @Test
    @DisplayName("Callback rate limit returns 429 and journals RATE_LIMITED")
    void callbackRateLimitedReturns429() throws Exception {
        settingsService.update(hostUserId, Map.of("rate_limit_payway_callback_per_minute", "3"));

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/v1/payments/payway/callback")
                            .header("X-Forwarded-For", TEST_IP_RATE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .param("tran_id", "TT_RATE_PROBE_UNKNOWN")
                            .param("status", "0"))
                    .andExpect(status().isBadRequest());
        }

        mockMvc.perform(post("/api/v1/payments/payway/callback")
                        .header("X-Forwarded-For", TEST_IP_RATE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .param("tran_id", "TT_RATE_PROBE_UNKNOWN")
                        .param("status", "0"))
                .andExpect(status().isTooManyRequests());

        List<PaymentEvent> events = paymentEventRepository.findByTranIdOrderByCreatedAtDesc("TT_RATE_PROBE_UNKNOWN");
        assertEquals(4, events.size());
        assertEquals(3, events.stream().filter(e -> "ERROR".equals(e.getOutcome())).count());
        assertEquals(1, events.stream()
                .filter(e -> "RATE_LIMITED".equals(e.getOutcome())).count());
    }

    @Test
    @DisplayName("Forensics payload is truncated to 8000 characters")
    void payloadIsTruncatedToMax() {
        paymentEventService.record("TT_TRUNC_TEST", "CALLBACK", "ERROR",
                "x".repeat(PaymentEventService.MAX_PAYLOAD_CHARS + 1000), null);

        PaymentEvent event = paymentEventRepository.findByTranIdOrderByCreatedAtDesc("TT_TRUNC_TEST")
                .stream().findFirst().orElseThrow();
        assertEquals(PaymentEventService.MAX_PAYLOAD_CHARS, event.getPayload().length());
    }

    @Test
    @DisplayName("Security: payment_events repository is append-only (no update/delete methods)")
    void repositoryIsAppendOnly() {
        for (Method method : PaymentEventRepository.class.getDeclaredMethods()) {
            String name = method.getName();
            assertFalse(name.startsWith("delete"), "append-only repository must not expose " + name);
            assertFalse(name.startsWith("update"), "append-only repository must not expose " + name);
            assertFalse(name.startsWith("saveAll"), "append-only repository must not expose " + name);
        }
    }
}

package com.tongtin;

import com.tongtin.common.util.PhoneUtil;
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
import com.tongtin.subscription.entity.SubscriptionOrder;
import com.tongtin.subscription.repository.SubscriptionOrderRepository;
import com.tongtin.subscription.service.SubscriptionService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class CheckoutIdempotencyTests {

    @Autowired
    private AuthService authService;

    @Autowired
    private SubscriptionService subscriptionService;

    @Autowired
    private SubscriptionOrderRepository orderRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OwnerAccountRepository ownerAccountRepository;

    @Autowired
    private SettingsService settingsService;

    @PersistenceContext
    private EntityManager entityManager;

    private Long hostUserId;
    private Long hostOwnerId;

    @BeforeEach
    void setUp() {
        String phone = "0977111333";
        authService.registerOwner(new RegisterOwnerRequest(
                "Chủ Hụi Idem", phone, "password123", "password123", null, null, true));

        User user = userRepository.findByPhone(PhoneUtil.normalize(phone)).orElseThrow();
        OwnerAccount owner = ownerAccountRepository.findByUserId(user.getId()).orElseThrow();
        hostUserId = user.getId();
        hostOwnerId = owner.getId();
    }

    private SubscriptionPlanDto planAt(int index) {
        List<SubscriptionPlanDto> plans = subscriptionService.getActivePlans();
        assertTrue(plans.size() > index, "expected at least " + (index + 1) + " active plans");
        return plans.get(index);
    }

    private PayWayCheckoutResponse checkout(Long planId) {
        return subscriptionService.checkoutPayWay(hostUserId, hostOwnerId,
                new PayWayCheckoutRequest(planId, null, null, null, null));
    }

    private long orderCount() {
        return orderRepository.findByOwnerIdOrderByCreatedAtDesc(hostOwnerId).size();
    }

    @Test
    @DisplayName("Repeated checkout for the same plan reuses the PENDING order")
    void testRepeatedCheckoutReusesPendingOrder() {
        PayWayCheckoutResponse first = checkout(planAt(0).id());
        PayWayCheckoutResponse second = checkout(planAt(0).id());

        assertEquals(first.tranId(), second.tranId());
        assertEquals(first.reqTime(), second.reqTime());
        assertEquals(1, orderCount());

        SubscriptionOrder order = orderRepository.findByTranId(second.tranId()).orElseThrow();
        assertEquals("PENDING", order.getStatus());
        assertNotNull(order.getPaywayHash());
    }

    @Test
    @DisplayName("Checkout for a different plan creates a separate order")
    void testDifferentPlanCreatesSeparateOrder() {
        PayWayCheckoutResponse first = checkout(planAt(0).id());
        PayWayCheckoutResponse second = checkout(planAt(1).id());

        assertNotEquals(first.tranId(), second.tranId());
        assertEquals(2, orderCount());
    }

    @Test
    @DisplayName("A PAID order is never reused by a later checkout")
    void testPaidOrderIsNotReused() {
        PayWayCheckoutResponse paid = checkout(planAt(0).id());
        subscriptionService.verifyAndActivateOrder(paid.tranId(), "ABA_123", null);

        PayWayCheckoutResponse next = checkout(planAt(0).id());

        assertNotEquals(paid.tranId(), next.tranId());
        assertEquals(2, orderCount());
    }

    @Test
    @DisplayName("A PENDING order older than the reuse window creates a new order")
    void testExpiredWindowCreatesNewOrder() {
        PayWayCheckoutResponse first = checkout(planAt(0).id());

        entityManager.createNativeQuery(
                        "UPDATE subscription_orders SET created_at = now() - interval '1 hour' WHERE tran_id = :t")
                .setParameter("t", first.tranId())
                .executeUpdate();
        entityManager.flush();

        PayWayCheckoutResponse second = checkout(planAt(0).id());

        assertNotEquals(first.tranId(), second.tranId());
        assertEquals(2, orderCount());
    }

    @Test
    @DisplayName("Reuse window 0 disables order reuse")
    void testReuseWindowZeroDisablesReuse() {
        settingsService.update(hostUserId, Map.of("checkout_pending_reuse_minutes", "0"));

        PayWayCheckoutResponse first = checkout(planAt(0).id());
        PayWayCheckoutResponse second = checkout(planAt(0).id());

        assertNotEquals(first.tranId(), second.tranId());
        assertEquals(2, orderCount());
    }
}

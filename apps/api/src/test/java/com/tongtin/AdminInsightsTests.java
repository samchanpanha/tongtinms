package com.tongtin;

import com.tongtin.common.security.JwtService;
import com.tongtin.common.util.PhoneUtil;
import com.tongtin.identity.dto.RegisterOwnerRequest;
import com.tongtin.identity.entity.OwnerAccount;
import com.tongtin.identity.entity.User;
import com.tongtin.identity.entity.UserRole;
import com.tongtin.identity.repository.OwnerAccountRepository;
import com.tongtin.identity.repository.UserRepository;
import com.tongtin.identity.repository.UserRoleRepository;
import com.tongtin.identity.service.AuthService;
import com.tongtin.subscription.dto.AdminInsightsDto;
import com.tongtin.subscription.dto.PayWayCheckoutRequest;
import com.tongtin.subscription.dto.PayWayCheckoutResponse;
import com.tongtin.subscription.dto.SubscriptionPlanDto;
import com.tongtin.subscription.service.SubscriptionService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class AdminInsightsTests {

    @Autowired
    private AuthService authService;

    @Autowired
    private SubscriptionService subscriptionService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OwnerAccountRepository ownerAccountRepository;

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
        String phone = "0977555666";
        authService.registerOwner(new RegisterOwnerRequest(
                "Chủ Hụi Insight", phone, "password123", "password123", null, null, true));

        User user = userRepository.findByPhone(PhoneUtil.normalize(phone)).orElseThrow();
        OwnerAccount owner = ownerAccountRepository.findByUserId(user.getId()).orElseThrow();
        hostUserId = user.getId();
        hostOwnerId = owner.getId();

        String adminPhone = "0988666777";
        if (!userRepository.existsByPhone(PhoneUtil.normalize(adminPhone))) {
            User admin = new User();
            admin.setPhone(PhoneUtil.normalize(adminPhone));
            admin.setFullName("Insight Admin");
            admin.setPasswordHash(passwordEncoder.encode("admin1234"));
            admin.setStatus("ACTIVE");
            userRepository.saveAndFlush(admin);
            userRoleRepository.save(new UserRole(admin.getId(), "ADMIN"));
            adminUserId = admin.getId();
        } else {
            adminUserId = userRepository.findByPhone(PhoneUtil.normalize(adminPhone)).orElseThrow().getId();
        }
    }

    private SubscriptionPlanDto planAt(int index) {
        List<SubscriptionPlanDto> plans = subscriptionService.getActivePlans();
        assertTrue(plans.size() > index, "expected at least " + (index + 1) + " active plans");
        return plans.get(index);
    }

    private PayWayCheckoutResponse payAndGetCheckout(Long planId) {
        PayWayCheckoutResponse checkout = subscriptionService.checkoutPayWay(
                hostUserId, hostOwnerId, new PayWayCheckoutRequest(planId, null, null, null, null));
        subscriptionService.verifyAndActivateOrder(checkout.tranId(), "ABA_INSIGHT_" + planId, null);
        return checkout;
    }

    @Test
    @DisplayName("Insights endpoint requires admin: anon 401, host 403, admin 200")
    void testInsightsAuth() throws Exception {
        mockMvc.perform(get("/api/v1/admin/insights"))
                .andExpect(status().isUnauthorized());

        String hostToken = jwtService.accessToken(hostUserId, hostOwnerId, List.of("HOST"));
        mockMvc.perform(get("/api/v1/admin/insights")
                        .header("Authorization", "Bearer " + hostToken))
                .andExpect(status().isForbidden());

        String adminToken = jwtService.accessToken(adminUserId, null, List.of("ADMIN"));
        mockMvc.perform(get("/api/v1/admin/insights")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revenueByPlan").isArray())
                .andExpect(jsonPath("$.totalsByCurrency").isArray())
                .andExpect(jsonPath("$.cohorts").isArray());
    }

    @Test
    @DisplayName("Revenue counts only PAID orders, grouped per plan, summed per currency")
    void testRevenuePerPlanCountsOnlyPaidOrders() {
        SubscriptionPlanDto usdPlan = planAt(0);

        payAndGetCheckout(usdPlan.id());
        // Second checkout for the same plan stays PENDING (paid order is never reused)
        subscriptionService.checkoutPayWay(
                hostUserId, hostOwnerId, new PayWayCheckoutRequest(usdPlan.id(), null, null, null, null));

        AdminInsightsDto insights = subscriptionService.getAdminInsights();

        AdminInsightsDto.PlanRevenue revenue = insights.revenueByPlan().stream()
                .filter(r -> r.planId().equals(usdPlan.id()))
                .findFirst()
                .orElse(null);
        assertNotNull(revenue, "expected revenue row for the paid plan");
        assertEquals(1, revenue.paidOrders(), "PENDING order must not be counted");
        assertEquals(usdPlan.priceMinor().longValue(), revenue.revenueMinor());
        assertEquals("USD", revenue.currency());

        AdminInsightsDto.CurrencyTotal usdTotal = insights.totalsByCurrency().stream()
                .filter(t -> t.currency().equals("USD"))
                .findFirst()
                .orElse(null);
        assertNotNull(usdTotal);
        assertTrue(usdTotal.paidOrders() >= 1);
        assertTrue(usdTotal.revenueMinor() >= usdPlan.priceMinor());
    }

    @Test
    @DisplayName("KHR plan revenue lands in its own currency total")
    void testKhrRevenueIsSeparateCurrencyTotal() {
        SubscriptionPlanDto khrPlan = planAt(0);
        List<SubscriptionPlanDto> plans = subscriptionService.getActivePlans();
        SubscriptionPlanDto khr = plans.stream()
                .filter(p -> "KHR".equals(p.currency()))
                .findFirst()
                .orElseGet(() -> khrPlan); // fall through if seed changes

        if (!"KHR".equals(khr.currency())) {
            return; // no KHR plan seeded — nothing to assert
        }
        payAndGetCheckout(khr.id());

        AdminInsightsDto insights = subscriptionService.getAdminInsights();

        AdminInsightsDto.CurrencyTotal khrTotal = insights.totalsByCurrency().stream()
                .filter(t -> t.currency().equals("KHR"))
                .findFirst()
                .orElse(null);
        assertNotNull(khrTotal, "expected a KHR currency total");
        assertEquals(1, khrTotal.paidOrders());
        assertEquals(khr.priceMinor().longValue(), khrTotal.revenueMinor());
    }

    @Test
    @DisplayName("Current-month cohort includes the freshly registered host as active")
    void testCohortsIncludeCurrentMonthHost() {
        AdminInsightsDto insights = subscriptionService.getAdminInsights();

        String currentMonth = LocalDate.now().toString().substring(0, 7);
        AdminInsightsDto.Cohort cohort = insights.cohorts().stream()
                .filter(c -> c.month().equals(currentMonth))
                .findFirst()
                .orElse(null);

        assertNotNull(cohort, "expected a cohort for the current month");
        assertTrue(cohort.registered() >= 1);
        assertTrue(cohort.activeNow() >= 1, "new trial host must count as active");
        assertTrue(cohort.registered() >= cohort.activeNow() + cohort.churned());
    }
}

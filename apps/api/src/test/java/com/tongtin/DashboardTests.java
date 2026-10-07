package com.tongtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class DashboardTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String hostAPhone;
    private String hostBPhone;
    private String hostAToken;
    private String hostBToken;

    @BeforeEach
    void registerTwoHosts() throws Exception {
        hostAPhone = "0988" + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
        hostBPhone = "0977" + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
        hostAToken = registerHost(hostAPhone);
        hostBToken = registerHost(hostBPhone);
    }

    @AfterEach
    void cleanUp() {
        String filter = ownerFilter();
        String groupIds = "(SELECT id FROM groups WHERE owner_id IN (SELECT id FROM owner_accounts WHERE user_id IN (SELECT id FROM users WHERE " + filter + ")))";
        String cycleIds = "(SELECT c.id FROM cycles c WHERE c.group_id IN " + groupIds + ")";
        String ledgerIds = "(SELECT l.id FROM ledger_entries l WHERE l.cycle_id IN " + cycleIds + ")";
        jdbcTemplate.update("DELETE FROM audit_events WHERE actor_user_id IN (SELECT id FROM users WHERE " + filter + ")");
        jdbcTemplate.update("DELETE FROM payment_allocations WHERE ledger_entry_id IN " + ledgerIds);
        jdbcTemplate.update("DELETE FROM payments WHERE group_id IN " + groupIds);
        jdbcTemplate.update("DELETE FROM ledger_entries WHERE cycle_id IN " + cycleIds);
        jdbcTemplate.update("DELETE FROM bids WHERE cycle_id IN " + cycleIds);
        jdbcTemplate.update("UPDATE group_shares SET won_cycle_id = NULL WHERE group_id IN " + groupIds);
        jdbcTemplate.update("DELETE FROM cycles WHERE group_id IN " + groupIds);
        jdbcTemplate.update("DELETE FROM group_shares WHERE group_id IN " + groupIds);
        jdbcTemplate.update("DELETE FROM groups WHERE owner_id IN (SELECT id FROM owner_accounts WHERE user_id IN (SELECT id FROM users WHERE " + filter + "))");
        jdbcTemplate.update("DELETE FROM member_profiles WHERE owner_id IN (SELECT id FROM owner_accounts WHERE user_id IN (SELECT id FROM users WHERE " + filter + "))");
        jdbcTemplate.update("DELETE FROM owner_accounts WHERE user_id IN (SELECT id FROM users WHERE " + filter + ")");
        jdbcTemplate.update("DELETE FROM user_roles WHERE user_id IN (SELECT id FROM users WHERE " + filter + ")");
        jdbcTemplate.update("DELETE FROM users WHERE " + filter);
    }

    private String ownerFilter() {
        return "phone IN ('" + hostAPhone + "','" + hostBPhone + "','+84" + hostAPhone.substring(1) + "','+84" + hostBPhone.substring(1) + "')";
    }

    private String uniqueIp() {
        return "10.17." + ThreadLocalRandom.current().nextInt(256) + "." + ThreadLocalRandom.current().nextInt(256);
    }

    private String randomPhone() {
        return "0966" + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
    }

    private String registerHost(String phone) throws Exception {
        String body = """
                {"fullName":"Chu Hoi","phone":"%s","password":"password123","confirmPassword":"password123","acceptTerms":true}
                """.formatted(phone);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register-owner")
                        .header("X-Forwarded-For", uniqueIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("tokens").path("accessToken").asText();
    }

    private long createGroup(String token, String body, boolean start) throws Exception {
        MvcResult g = mockMvc.perform(post("/api/v1/groups")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        long groupId = objectMapper.readTree(g.getResponse().getContentAsString()).path("id").asLong();
        if (start) {
            for (int i = 0; i < 3; i++) {
                String memberBody = "{\"fullName\":\"TV %d\",\"phone\":\"%s\"}".formatted(i, randomPhone());
                MvcResult m = mockMvc.perform(post("/api/v1/members")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(memberBody))
                        .andExpect(status().isCreated())
                        .andReturn();
                long memberId = objectMapper.readTree(m.getResponse().getContentAsString()).path("id").asLong();
                mockMvc.perform(post("/api/v1/groups/" + groupId + "/shares")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"memberProfileId\":%d,\"count\":1}".formatted(memberId)))
                        .andExpect(status().isCreated());
            }
            mockMvc.perform(post("/api/v1/groups/" + groupId + "/start").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        }
        return groupId;
    }

    private String fixedGroupBody(String name, String currency, long hostFeeMinor) {
        return """
                {"name":"%s","type":"FIXED","baseAmount":1000000,"shareCount":3,"cycleUnit":"MONTH",
                 "cycleCount":3,"currency":"%s","bidCloseOffset":7,
                 "hostFeeType":"FIXED_PER_CYCLE","hostFeeMinor":%d}
                """.formatted(name, currency, hostFeeMinor);
    }

    private long settleCycle(String token, long groupId) throws Exception {
        MvcResult open = mockMvc.perform(post("/api/v1/groups/" + groupId + "/cycles/open")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();
        long cycleId = objectMapper.readTree(open.getResponse().getContentAsString()).path("id").asLong();
        mockMvc.perform(post("/api/v1/cycles/" + cycleId + "/close-and-calculate")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/cycles/" + cycleId + "/confirm-payout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        return cycleId;
    }

    private JsonNode dashboard(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/host/dashboard")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode currencyRow(JsonNode dashboard, String code) {
        for (JsonNode c : dashboard.path("currencies")) {
            if (code.equals(c.path("currency").asText())) {
                return c;
            }
        }
        return null;
    }

    private JsonNode groupRow(JsonNode dashboard, String name) {
        for (JsonNode g : dashboard.path("groups")) {
            if (name.equals(g.path("name").asText())) {
                return g;
            }
        }
        return null;
    }

    private long contributionEntry(String token, long groupId, long cycleId) throws Exception {
        MvcResult shares = mockMvc.perform(get("/api/v1/groups/" + groupId + "/shares")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        long shareId = objectMapper.readTree(shares.getResponse().getContentAsString()).path(1).path("id").asLong();
        return jdbcTemplate.queryForObject(
                "SELECT id FROM ledger_entries WHERE cycle_id = ? AND share_id = ? AND type = 'CONTRIBUTION'",
                Long.class, cycleId, shareId);
    }

    private void pay(String token, long groupId, long entryId, long amountMinor) throws Exception {
        mockMvc.perform(post("/api/v1/groups/" + groupId + "/payments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amountMinor":%d,"currency":"VND",
                                 "allocations":[{"ledgerEntryId":%d,"amountMinor":%d}]}
                                """.formatted(amountMinor, entryId, amountMinor)))
                .andExpect(status().isCreated());
    }

    @Test
    void emptyDashboardReturnsNoData() throws Exception {
        JsonNode dash = dashboard(hostAToken);
        assertThat(dash.path("groups")).hasSize(0);
        assertThat(dash.path("currencies")).hasSize(0);
    }

    @Test
    void dashboardShowsGroupsNextDueUnpaidAndProfitPerCurrency() throws Exception {
        long vndGroup = createGroup(hostAToken, fixedGroupBody("Hoi VND", "VND", 100_000), true);
        long vndCycle = settleCycle(hostAToken, vndGroup);
        createGroup(hostAToken, fixedGroupBody("Hoi USD", "USD", 0), false);

        JsonNode dash = dashboard(hostAToken);
        assertThat(dash.path("groups")).hasSize(2);

        JsonNode vnd = groupRow(dash, "Hoi VND");
        assertThat(vnd).isNotNull();
        assertThat(vnd.path("id").asLong()).isEqualTo(vndGroup);
        assertThat(vnd.path("status").asText()).isEqualTo("RUNNING");
        assertThat(vnd.path("currency").asText()).isEqualTo("VND");
        assertThat(vnd.path("shareCount").asInt()).isEqualTo(3);
        assertThat(vnd.path("cycleCount").asInt()).isEqualTo(3);
        assertThat(vnd.path("currentCycleNo").asInt()).isEqualTo(1);
        assertThat(vnd.path("currentCycleStatus").asText()).isEqualTo("SETTLED");
        assertThat(vnd.path("nextDueAt").asText()).isNotBlank();
        assertThat(vnd.path("unpaidCount").asLong()).isEqualTo(2);
        assertThat(vnd.path("overdueCount").asLong()).isZero();
        assertThat(vnd.path("hostProfitMinor").asLong()).isEqualTo(100_000);

        JsonNode usd = groupRow(dash, "Hoi USD");
        assertThat(usd).isNotNull();
        assertThat(usd.path("status").asText()).isEqualTo("DRAFT");
        assertThat(usd.path("currentCycleNo").isNull()).isTrue();
        assertThat(usd.path("currentCycleStatus").isNull()).isTrue();
        assertThat(usd.path("nextDueAt").isNull()).isTrue();
        assertThat(usd.path("unpaidCount").asLong()).isZero();
        assertThat(usd.path("overdueCount").asLong()).isZero();
        assertThat(usd.path("hostProfitMinor").asLong()).isZero();

        assertThat(dash.path("currencies")).hasSize(2);
        JsonNode vndProfit = currencyRow(dash, "VND");
        assertThat(vndProfit.path("amountMinor").asLong()).isEqualTo(100_000);
        assertThat(vndProfit.path("exponent").asInt()).isZero();
        assertThat(vndProfit.path("symbol").asText()).isEqualTo("d");
        JsonNode usdProfit = currencyRow(dash, "USD");
        assertThat(usdProfit.path("amountMinor").asLong()).isZero();
        assertThat(usdProfit.path("exponent").asInt()).isEqualTo(2);
        assertThat(usdProfit.path("symbol").asText()).isEqualTo("$");

        assertThat(vndCycle).isPositive();
    }

    @Test
    void profitCountsSettledCyclesOnly() throws Exception {
        long groupId = createGroup(hostAToken, fixedGroupBody("Hoi Pending", "VND", 75_000), true);
        MvcResult open = mockMvc.perform(post("/api/v1/groups/" + groupId + "/cycles/open")
                        .header("Authorization", "Bearer " + hostAToken))
                .andExpect(status().isCreated())
                .andReturn();
        long cycleId = objectMapper.readTree(open.getResponse().getContentAsString()).path("id").asLong();
        mockMvc.perform(post("/api/v1/cycles/" + cycleId + "/close-and-calculate")
                        .header("Authorization", "Bearer " + hostAToken))
                .andExpect(status().isOk());

        JsonNode pending = dashboard(hostAToken);
        assertThat(groupRow(pending, "Hoi Pending").path("hostProfitMinor").asLong()).isZero();
        assertThat(groupRow(pending, "Hoi Pending").path("currentCycleStatus").asText()).isEqualTo("PAYOUT_PENDING");

        mockMvc.perform(post("/api/v1/cycles/" + cycleId + "/confirm-payout")
                        .header("Authorization", "Bearer " + hostAToken))
                .andExpect(status().isOk());

        JsonNode settled = dashboard(hostAToken);
        assertThat(groupRow(settled, "Hoi Pending").path("hostProfitMinor").asLong()).isEqualTo(75_000);
    }

    @Test
    void overdueObligationSurfacesAsNextDueAndClearsOnPayment() throws Exception {
        long groupId = createGroup(hostAToken, fixedGroupBody("Hoi Due", "VND", 100_000), true);
        long cycleId = settleCycle(hostAToken, groupId);
        long entryId = contributionEntry(hostAToken, groupId, cycleId);
        jdbcTemplate.update("UPDATE ledger_entries SET due_at = now() - interval '3 days' WHERE id = ?", entryId);

        JsonNode before = dashboard(hostAToken);
        JsonNode row = groupRow(before, "Hoi Due");
        assertThat(row.path("overdueCount").asLong()).isEqualTo(1);
        assertThat(row.path("unpaidCount").asLong()).isEqualTo(2);
        Instant nextDue = Instant.parse(row.path("nextDueAt").asText());
        assertThat(nextDue).isBefore(Instant.now().minus(2, ChronoUnit.DAYS));

        pay(hostAToken, groupId, entryId, 1_000_000L);

        JsonNode after = dashboard(hostAToken);
        JsonNode rowAfter = groupRow(after, "Hoi Due");
        assertThat(rowAfter.path("overdueCount").asLong()).isZero();
        assertThat(rowAfter.path("unpaidCount").asLong()).isEqualTo(1);
        assertThat(Instant.parse(rowAfter.path("nextDueAt").asText())).isAfter(Instant.now());
    }

    @Test
    void crossOwnerIsolation() throws Exception {
        long groupId = createGroup(hostAToken, fixedGroupBody("Hoi A", "VND", 100_000), true);
        settleCycle(hostAToken, groupId);

        JsonNode a = dashboard(hostAToken);
        assertThat(a.path("groups")).hasSize(1);

        JsonNode b = dashboard(hostBToken);
        assertThat(b.path("groups")).hasSize(0);
        assertThat(b.path("currencies")).hasSize(0);
    }

    @Test
    void dashboardRequiresAuth() throws Exception {
        mockMvc.perform(get("/api/v1/host/dashboard"))
                .andExpect(status().isUnauthorized());
    }
}
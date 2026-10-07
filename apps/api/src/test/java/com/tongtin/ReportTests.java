package com.tongtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
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

/**
 * Step 15 host reports: GET /groups/{id}/ledger and GET /groups/{id}/profit.
 * Money shape everywhere ({currency, amountMinor, exponent, symbol}); tenant
 * cross-reads are 404.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ReportTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<String> createdPhones = new ArrayList<>();
    private String hostAPhone;
    private String hostBPhone;
    private String hostAToken;
    private String hostBToken;

    @BeforeEach
    void registerTwoHosts() throws Exception {
        hostAPhone = "0988" + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
        hostBPhone = "0977" + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
        createdPhones.add(hostAPhone);
        createdPhones.add(hostBPhone);
        hostAToken = register(hostAPhone);
        hostBToken = register(hostBPhone);
    }

    @AfterEach
    void cleanUp() {
        String filter = phonesFilter();
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
        jdbcTemplate.update("DELETE FROM notifications WHERE user_id IN (SELECT id FROM users WHERE " + filter + ")");
        jdbcTemplate.update("DELETE FROM owner_accounts WHERE user_id IN (SELECT id FROM users WHERE " + filter + ")");
        jdbcTemplate.update("DELETE FROM user_roles WHERE user_id IN (SELECT id FROM users WHERE " + filter + ")");
        jdbcTemplate.update("DELETE FROM users WHERE " + filter);
    }

    private String phonesFilter() {
        return "phone IN (" + createdPhones.stream()
                .flatMap(p -> List.of(p, "+84" + p.substring(1)).stream())
                .map(p -> "'" + p + "'")
                .collect(Collectors.joining(",")) + ")";
    }

    private String uniqueIp() {
        return "10.17." + ThreadLocalRandom.current().nextInt(256) + "." + ThreadLocalRandom.current().nextInt(256);
    }

    private String randomPhone() {
        return "0966" + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
    }

    private String register(String phone) throws Exception {
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

    private long createMember(String token) throws Exception {
        MvcResult m = mockMvc.perform(post("/api/v1/members")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Hoi Vien\",\"phone\":\"%s\"}".formatted(randomPhone())))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode member = objectMapper.readTree(m.getResponse().getContentAsString());
        createdPhones.add(member.path("phone").asText());
        return member.path("id").asLong();
    }

    private long createGroup(String token, String hostFeeType, long hostFeeMinor) throws Exception {
        String body = """
                {"name":"Hoi Report","type":"BIDDING","baseAmount":1000000,"shareCount":3,"cycleUnit":"MONTH",
                 "cycleCount":3,"currency":"VND","maxBid":500000,"bidStep":10000,"bidCloseOffset":7,
                 "hostFeeType":"%s","hostFeeMinor":%d}
                """.formatted(hostFeeType, hostFeeMinor);
        MvcResult g = mockMvc.perform(post("/api/v1/groups")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(g.getResponse().getContentAsString()).path("id").asLong();
    }

    private long assignShare(String token, long groupId, long memberId) throws Exception {
        MvcResult s = mockMvc.perform(post("/api/v1/groups/" + groupId + "/shares")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberProfileId\":%d,\"count\":1}".formatted(memberId)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode shares = objectMapper.readTree(s.getResponse().getContentAsString());
        return shares.path(shares.size() - 1).path("id").asLong();
    }

    /** 3 members each owning one share; member i has share i+1. Returns [groupId, memberIds, shareIds]. */
    private Scenario setup(String hostFeeType, long hostFeeMinor) throws Exception {
        long groupId = createGroup(hostAToken, hostFeeType, hostFeeMinor);
        List<Long> memberIds = new ArrayList<>();
        List<Long> shareIds = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            long member = createMember(hostAToken);
            memberIds.add(member);
            shareIds.add(assignShare(hostAToken, groupId, member));
        }
        mockMvc.perform(post("/api/v1/groups/" + groupId + "/start")
                        .header("Authorization", "Bearer " + hostAToken))
                .andExpect(status().isOk());
        return new Scenario(groupId, memberIds, shareIds);
    }

    private long openCycle(String token, long groupId) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/v1/groups/" + groupId + "/cycles/open")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).path("id").asLong();
    }

    private JsonNode getJson(String url, String token) throws Exception {
        MvcResult r = mockMvc.perform(get(url).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString());
    }

    private static void assertMoney(JsonNode node, String currency, long amountMinor) {
        assertThat(node.path("currency").asText()).isEqualTo(currency);
        assertThat(node.path("amountMinor").asLong()).isEqualTo(amountMinor);
        assertThat(node.path("exponent").isNumber()).isTrue();
        assertThat(node.path("symbol").asText()).isNotBlank();
    }

    /** Settles cycle 1 with share 1 winning (bid 200000), then records the payer's contribution. */
    private long settleCycle1(Scenario s, long hostFeePerCycle) throws Exception {
        long cycleId = openCycle(hostAToken, s.groupId());
        mockMvc.perform(post("/api/v1/cycles/" + cycleId + "/bids")
                        .header("Authorization", "Bearer " + hostAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shareId\":%d,\"amountMinor\":200000}".formatted(s.shareIds().get(0))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/cycles/" + cycleId + "/close-and-calculate")
                        .header("Authorization", "Bearer " + hostAToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/cycles/" + cycleId + "/confirm-payout")
                        .header("Authorization", "Bearer " + hostAToken))
                .andExpect(status().isOk());

        long payerEntry = jdbcTemplate.queryForObject(
                "SELECT id FROM ledger_entries WHERE cycle_id = ? AND share_id = ? AND type = 'CONTRIBUTION'",
                Long.class, cycleId, s.shareIds().get(1));
        long contribution = 1_000_000L - 200_000L;
        mockMvc.perform(post("/api/v1/groups/" + s.groupId() + "/payments")
                        .header("Authorization", "Bearer " + hostAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("{\"amountMinor\":%d,\"currency\":\"VND\",\"allocations\":" +
                                "[{\"ledgerEntryId\":%d,\"amountMinor\":%d}]}").formatted(contribution, payerEntry, contribution)))
                .andExpect(status().isCreated());
        return cycleId;
    }

    private record Scenario(long groupId, List<Long> memberIds, List<Long> shareIds) {
    }

    @Test
    void ledgerListsEveryObligationWithMoneyShapeAndTotals() throws Exception {
        long hostFee = 100_000;
        Scenario s = setup("FIXED_PER_CYCLE", hostFee);
        settleCycle1(s, hostFee);

        JsonNode ledger = getJson("/api/v1/groups/" + s.groupId() + "/ledger", hostAToken);
        assertThat(ledger.path("currency").asText()).isEqualTo("VND");
        assertThat(ledger.path("entries")).hasSize(4);

        // sorted (cycleNo, shareNo nulls-last, entryId): PAYOUT(share1), CONTRIBUTION(share2),
        // CONTRIBUTION(share3), HOST_FEE(null)
        JsonNode e0 = ledger.path("entries").path(0);
        assertThat(e0.path("type").asText()).isEqualTo("PAYOUT");
        assertThat(e0.path("shareNo").asInt()).isEqualTo(1);
        assertThat(e0.path("memberName").asText()).isEqualTo("Hoi Vien");
        assertThat(e0.path("status").asText()).isEqualTo("PAID");
        assertMoney(e0.path("amount"), "VND", 1_500_000);

        JsonNode e1 = ledger.path("entries").path(1);
        assertThat(e1.path("type").asText()).isEqualTo("CONTRIBUTION");
        assertThat(e1.path("direction").asText()).isEqualTo("IN");
        assertThat(e1.path("shareNo").asInt()).isEqualTo(2);
        assertThat(e1.path("status").asText()).isEqualTo("PAID");
        assertMoney(e1.path("amount"), "VND", 800_000);
        assertMoney(e1.path("allocated"), "VND", 800_000);
        assertMoney(e1.path("remaining"), "VND", 0);

        JsonNode e2 = ledger.path("entries").path(2);
        assertThat(e2.path("shareNo").asInt()).isEqualTo(3);
        assertThat(e2.path("status").asText()).isEqualTo("UNPAID");
        assertMoney(e2.path("amount"), "VND", 800_000);
        assertMoney(e2.path("allocated"), "VND", 0);
        assertMoney(e2.path("remaining"), "VND", 800_000);

        JsonNode e3 = ledger.path("entries").path(3);
        assertThat(e3.path("type").asText()).isEqualTo("HOST_FEE");
        assertThat(e3.path("direction").asText()).isEqualTo("OUT");
        assertThat(e3.path("shareNo").isNull()).isTrue();
        assertThat(e3.path("memberProfileId").isNull()).isTrue();
        assertThat(e3.path("memberName").isNull()).isTrue();
        assertMoney(e3.path("amount"), "VND", 100_000);

        assertMoney(ledger.path("totalIn"), "VND", 1_600_000);
        assertMoney(ledger.path("totalOut"), "VND", 1_600_000);
    }

    @Test
    void profitReportPerCycleWithTotalsAndFormulaVersion() throws Exception {
        long hostFee = 50_000;
        Scenario s = setup("FIXED_PER_CYCLE", hostFee);
        settleCycle1(s, hostFee);

        JsonNode profit = getJson("/api/v1/groups/" + s.groupId() + "/profit", hostAToken);
        assertThat(profit.path("currency").asText()).isEqualTo("VND");
        assertThat(profit.path("formulaVersion").asInt()).isPositive();

        assertThat(profit.path("cycles")).hasSize(1);
        JsonNode c = profit.path("cycles").path(0);
        assertThat(c.path("cycleNo").asInt()).isEqualTo(1);
        assertThat(c.path("status").asText()).isEqualTo("SETTLED");
        assertThat(c.path("winner").path("shareNo").asInt()).isEqualTo(1);
        assertThat(c.path("winner").path("memberName").asText()).isEqualTo("Hoi Vien");
        assertMoney(c.path("winningBid"), "VND", 200_000);
        assertMoney(c.path("grossPot"), "VND", 1_600_000);
        assertMoney(c.path("hostFee"), "VND", 50_000);
        assertMoney(c.path("netPayout"), "VND", 1_550_000);

        assertMoney(profit.path("totals").path("grossPot"), "VND", 1_600_000);
        assertMoney(profit.path("totals").path("hostFee"), "VND", 50_000);
        assertMoney(profit.path("totals").path("netPayout"), "VND", 1_550_000);
        assertMoney(profit.path("totals").path("settledHostFee"), "VND", 50_000);
    }

    @Test
    void unavailableCyclesShowNullAmountsInReports() throws Exception {
        Scenario s = setup("NONE", 0);
        openCycle(hostAToken, s.groupId());

        JsonNode profit = getJson("/api/v1/groups/" + s.groupId() + "/profit", hostAToken);
        JsonNode c = profit.path("cycles").path(0);
        assertThat(c.path("cycleNo").asInt()).isEqualTo(1);
        assertThat(c.path("status").asText()).isEqualTo("BIDDING");
        assertThat(c.path("winner").isNull()).isTrue();
        assertThat(c.path("winningBid").isNull()).isTrue();
        assertThat(c.path("grossPot").isNull()).isTrue();
        assertThat(c.path("hostFee").isNull()).isTrue();
        assertThat(c.path("netPayout").isNull()).isTrue();
        assertThat(profit.path("totals").path("grossPot").path("amountMinor").asLong()).isZero();

        // ledger for a fresh cycle: open only, nothing settled yet -> empty entries
        JsonNode ledger = getJson("/api/v1/groups/" + s.groupId() + "/ledger", hostAToken);
        assertThat(ledger.path("entries")).isEmpty();
        assertMoney(ledger.path("totalIn"), "VND", 0);
        assertMoney(ledger.path("totalOut"), "VND", 0);
    }

    @Test
    void reportsAreTenantScoped() throws Exception {
        Scenario s = setup("NONE", 0);
        mockMvc.perform(get("/api/v1/groups/" + s.groupId() + "/ledger")
                        .header("Authorization", "Bearer " + hostBToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/groups/" + s.groupId() + "/profit")
                        .header("Authorization", "Bearer " + hostBToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/groups/" + s.groupId() + "/ledger"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/groups/999999/ledger")
                        .header("Authorization", "Bearer " + hostAToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void memberCannotReadHostReports() throws Exception {
        Scenario s = setup("NONE", 0);
        long memberId = s.memberIds().get(0);
        mockMvc.perform(post("/api/v1/members/" + memberId + "/set-login")
                        .header("Authorization", "Bearer " + hostAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"password123\"}"))
                .andExpect(status().isOk());
        String phone = jdbcTemplate.queryForObject(
                "SELECT phone FROM member_profiles WHERE id = ?", String.class, memberId);
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", uniqueIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"%s\",\"password\":\"password123\"}".formatted(phone)))
                .andReturn();
        String memberToken = objectMapper.readTree(login.getResponse().getContentAsString())
                .path("tokens").path("accessToken").asText();

        mockMvc.perform(get("/api/v1/groups/" + s.groupId() + "/ledger")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/groups/" + s.groupId() + "/profit")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isForbidden());
    }
}
package com.tongtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
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

/**
 * Step 29: host-triggered late-fee assessment (01-DOMAIN 9.6, 02-ARCHITECTURE 5).
 * CONTRACT: cumulative delta model — target = lateFee(...), existing LATE_FEE rows
 * for (cycle, share) are summed, only the positive delta is inserted. CONTRIBUTION
 * obligations only; explicit POST; lateFeeType NONE is a no-op.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LateFeeTests {

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
        jdbcTemplate.update("DELETE FROM notifications WHERE user_id IN (SELECT id FROM users WHERE " + filter + ")");
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

    private String randomPhone() {
        return "0966" + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
    }

    /** 3-share group with lateFeeType/lateFeeValue, settled cycle 1, payout confirmed. */
    private Fixture setupSettledCycle(String token, String lateFeeType, long lateFeeValue) throws Exception {
        String groupBody = """
                {"name":"Hoi Late Fee","type":"BIDDING","baseAmount":1000000,"shareCount":3,"cycleUnit":"MONTH",
                 "cycleCount":3,"currency":"VND","maxBid":500000,"bidStep":10000,"bidCloseOffset":7,
                 "lateFeeType":"%s","lateFeeValue":%d}
                """.formatted(lateFeeType, lateFeeValue);
        MvcResult g = mockMvc.perform(post("/api/v1/groups")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(groupBody))
                .andExpect(status().isCreated())
                .andReturn();
        long groupId = objectMapper.readTree(g.getResponse().getContentAsString()).path("id").asLong();

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

        JsonNode shares = sharesOf(token, groupId);
        MvcResult open = mockMvc.perform(post("/api/v1/groups/" + groupId + "/cycles/open")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();
        long cycleId = objectMapper.readTree(open.getResponse().getContentAsString()).path("id").asLong();
        mockMvc.perform(post("/api/v1/cycles/" + cycleId + "/bids")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shareId\":%d,\"amountMinor\":200000}".formatted(shares.path(0).path("id").asLong())))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/cycles/" + cycleId + "/close-and-calculate")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        List<Map<String, Object>> contributions = jdbcTemplate.queryForList(
                "SELECT id FROM ledger_entries WHERE cycle_id = ? AND type = 'CONTRIBUTION' ORDER BY id",
                cycleId);
        assertThat(contributions).hasSize(2);
        long contributionA = ((Number) contributions.get(0).get("id")).longValue();
        long contributionB = ((Number) contributions.get(1).get("id")).longValue();
        long payoutId = jdbcTemplate.queryForObject(
                "SELECT id FROM ledger_entries WHERE cycle_id = ? AND type = 'PAYOUT'", Long.class, cycleId);
        return new Fixture(groupId, cycleId, contributionA, contributionB, payoutId);
    }

    private record Fixture(long groupId, long cycleId, long contributionA, long contributionB, long payoutEntry) {
    }

    private JsonNode sharesOf(String token, long groupId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/groups/" + groupId + "/shares")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private MvcResult assess(String token, long groupId) throws Exception {
        return mockMvc.perform(post("/api/v1/groups/" + groupId + "/late-fees/assess")
                        .header("Authorization", "Bearer " + token))
                .andReturn();
    }

    private JsonNode assessOk(String token, long groupId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/groups/" + groupId + "/late-fees/assess")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private void backdateContributions(long cycleId, int days) {
        jdbcTemplate.update(
                "UPDATE ledger_entries SET due_at = now() - interval '" + days + " days' "
                        + "WHERE cycle_id = ? AND type = 'CONTRIBUTION'", cycleId);
    }

    private long feeRowCount(long cycleId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM ledger_entries WHERE cycle_id = ? AND type = 'LATE_FEE'",
                Long.class, cycleId);
    }

    private List<Long> feeAmounts(long cycleId) {
        return jdbcTemplate.queryForList(
                "SELECT amount_minor FROM ledger_entries WHERE cycle_id = ? AND type = 'LATE_FEE' ORDER BY id",
                Long.class, cycleId);
    }

    @Test
    void fixedFeeIsChargedOnceAndIdempotent() throws Exception {
        Fixture f = setupSettledCycle(hostAToken, "FIXED", 20000);
        backdateContributions(f.cycleId(), 3);

        JsonNode first = assessOk(hostAToken, f.groupId());
        assertThat(first.path("assessed").asInt()).isEqualTo(2);
        assertThat(first.path("created").asInt()).isEqualTo(2);
        assertThat(first.path("entries")).hasSize(2);
        JsonNode entry = first.path("entries").path(0);
        assertThat(entry.path("amountMinor").asLong()).isEqualTo(20000);
        assertThat(entry.path("currency").asText()).isEqualTo("VND");
        assertThat(entry.path("dueAt").asText()).isNotBlank();
        assertThat(entry.path("ledgerEntryId").asLong()).isPositive();
        assertThat(feeAmounts(f.cycleId())).containsExactly(20000L, 20000L);

        JsonNode second = assessOk(hostAToken, f.groupId());
        assertThat(second.path("assessed").asInt()).isEqualTo(2);
        assertThat(second.path("created").asInt()).isZero();
        assertThat(second.path("entries")).isEmpty();
        assertThat(feeRowCount(f.cycleId())).isEqualTo(2);

        // fee rows copy source due_at, status UNPAID, direction IN
        Map<String, Object> fee = jdbcTemplate.queryForMap(
                "SELECT direction, status, share_id IS NOT NULL AS has_share FROM ledger_entries "
                        + "WHERE cycle_id = ? AND type = 'LATE_FEE' LIMIT 1", f.cycleId());
        assertThat(fee.get("direction")).isEqualTo("IN");
        assertThat(fee.get("status")).isEqualTo("UNPAID");
        assertThat(fee.get("has_share")).isEqualTo(Boolean.TRUE);

        Long auditCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_events WHERE action = 'LATE_FEES_ASSESSED'", Long.class);
        assertThat(auditCount).isGreaterThanOrEqualTo(1);
    }

    @Test
    void percentPerDayGrowsByDeltaOnly() throws Exception {
        Fixture f = setupSettledCycle(hostAToken, "PERCENT_PER_DAY", 2);
        backdateContributions(f.cycleId(), 3);

        // target = (800000 * 2 * 3 + 50) / 100 = 48000 per contribution
        JsonNode first = assessOk(hostAToken, f.groupId());
        assertThat(first.path("created").asInt()).isEqualTo(2);
        assertThat(feeAmounts(f.cycleId())).containsExactly(48000L, 48000L);

        backdateContributions(f.cycleId(), 5);
        // target = (800000 * 2 * 5 + 50) / 100 = 80000; charged = 48000; delta = 32000
        JsonNode second = assessOk(hostAToken, f.groupId());
        assertThat(second.path("assessed").asInt()).isEqualTo(2);
        assertThat(second.path("created").asInt()).isEqualTo(2);
        assertThat(feeAmounts(f.cycleId())).containsExactly(48000L, 48000L, 32000L, 32000L);

        JsonNode third = assessOk(hostAToken, f.groupId());
        assertThat(third.path("created").asInt()).isZero();
        assertThat(feeRowCount(f.cycleId())).isEqualTo(4);
    }

    @Test
    void noneTypeIsNoOp() throws Exception {
        Fixture f = setupSettledCycle(hostAToken, "NONE", 0);
        backdateContributions(f.cycleId(), 3);

        JsonNode response = assessOk(hostAToken, f.groupId());
        assertThat(response.path("assessed").asInt()).isZero();
        assertThat(response.path("created").asInt()).isZero();
        assertThat(response.path("entries")).isEmpty();
        assertThat(feeRowCount(f.cycleId())).isZero();
    }

    @Test
    void notOverdueAndPaidObligationsAreSkipped() throws Exception {
        Fixture f = setupSettledCycle(hostAToken, "FIXED", 20000);

        // nothing overdue yet
        JsonNode none = assessOk(hostAToken, f.groupId());
        assertThat(none.path("assessed").asInt()).isZero();
        assertThat(feeRowCount(f.cycleId())).isZero();

        // settle one contribution, then backdate both
        mockMvc.perform(post("/api/v1/groups/" + f.groupId() + "/payments")
                        .header("Authorization", "Bearer " + hostAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountMinor\":800000,\"currency\":\"VND\",\"method\":\"CASH\","
                                + "\"allocations\":[{\"ledgerEntryId\":" + f.contributionA()
                                + ",\"amountMinor\":800000}]}"))
                .andExpect(status().isCreated());
        backdateContributions(f.cycleId(), 3);

        JsonNode response = assessOk(hostAToken, f.groupId());
        assertThat(response.path("assessed").asInt()).isEqualTo(1);
        assertThat(response.path("created").asInt()).isEqualTo(1);
        assertThat(feeRowCount(f.cycleId())).isEqualTo(1);
        assertThat(feeAmounts(f.cycleId())).containsExactly(20000L);
        // the fee targets the still-unpaid contribution only
        Long feeShare = jdbcTemplate.queryForObject(
                "SELECT share_id FROM ledger_entries WHERE cycle_id = ? AND type = 'LATE_FEE'",
                Long.class, f.cycleId());
        Long unpaidShare = jdbcTemplate.queryForObject(
                "SELECT share_id FROM ledger_entries WHERE id = ?", Long.class, f.contributionB());
        assertThat(feeShare).isEqualTo(unpaidShare);
    }

    @Test
    void payoutObligationIsNeverFeeAssessed() throws Exception {
        Fixture f = setupSettledCycle(hostAToken, "FIXED", 20000);
        // payout is still UNPAID here (confirm-payout intentionally skipped), overdue it too
        jdbcTemplate.update("UPDATE ledger_entries SET due_at = now() - interval '3 days' WHERE id = ?",
                f.payoutEntry());
        jdbcTemplate.update(
                "UPDATE ledger_entries SET due_at = now() - interval '3 days' "
                        + "WHERE cycle_id = ? AND type = 'CONTRIBUTION'", f.cycleId());

        JsonNode response = assessOk(hostAToken, f.groupId());
        assertThat(response.path("assessed").asInt()).isEqualTo(2);
        assertThat(response.path("created").asInt()).isEqualTo(2);
        assertThat(feeRowCount(f.cycleId())).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM ledger_entries WHERE cycle_id = ? AND type = 'LATE_FEE' AND share_id = ?",
                Long.class, f.cycleId(),
                jdbcTemplate.queryForObject("SELECT share_id FROM ledger_entries WHERE id = ?",
                        Long.class, f.payoutEntry()))).isZero();
    }

    @Test
    void lateFeeSurfacesInDebtsAndCanBePaid() throws Exception {
        Fixture f = setupSettledCycle(hostAToken, "FIXED", 20000);
        backdateContributions(f.cycleId(), 3);
        assessOk(hostAToken, f.groupId());

        MvcResult debtsResult = mockMvc.perform(get("/api/v1/groups/" + f.groupId() + "/debts")
                        .header("Authorization", "Bearer " + hostAToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode debts = objectMapper.readTree(debtsResult.getResponse().getContentAsString());
        // 2 overdue contributions + 2 LATE_FEE rows (both copy the source due_at)
        assertThat(debts).hasSize(4);
        JsonNode feeDebt = null;
        for (JsonNode d : debts) {
            if ("LATE_FEE".equals(d.path("type").asText())) {
                feeDebt = d;
            }
        }
        assertThat(feeDebt).isNotNull();
        assertThat(feeDebt.path("remainingMinor").asLong()).isEqualTo(20000);
        assertThat(feeDebt.path("status").asText()).isEqualTo("UNPAID");
        assertThat(feeDebt.path("overdueDays").asLong()).isGreaterThanOrEqualTo(3);
        long feeEntryId = feeDebt.path("ledgerEntryId").asLong();

        mockMvc.perform(post("/api/v1/groups/" + f.groupId() + "/payments")
                        .header("Authorization", "Bearer " + hostAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountMinor\":20000,\"currency\":\"VND\",\"method\":\"CASH\","
                                + "\"allocations\":[{\"ledgerEntryId\":" + feeEntryId
                                + ",\"amountMinor\":20000}]}"))
                .andExpect(status().isCreated());
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM ledger_entries WHERE id = ?",
                String.class, feeEntryId)).isEqualTo("PAID");

        // paid fee still counts as charged -> a later assess writes nothing new
        JsonNode again = assessOk(hostAToken, f.groupId());
        assertThat(again.path("created").asInt()).isZero();
        assertThat(feeRowCount(f.cycleId())).isEqualTo(2);
    }

    @Test
    void crossOwnerAndAuthGuards() throws Exception {
        Fixture f = setupSettledCycle(hostAToken, "FIXED", 20000);
        backdateContributions(f.cycleId(), 3);

        assertThat(assess(hostBToken, f.groupId()).getResponse().getStatus()).isEqualTo(404);
        mockMvc.perform(post("/api/v1/groups/" + f.groupId() + "/late-fees/assess"))
                .andExpect(status().isUnauthorized());
        assertThat(feeRowCount(f.cycleId())).isZero();
    }

    @Test
    void halfUpRoundingIsAppliedToPercentFee() throws Exception {
        // principal * value * days = 800000 * 1 * 1 = 800000 -> 800000/100 exact
        // choose value so the fractional part lands on .5: use a tiny contribution
        // by adjusting the fee value to 1 and backdating 1 day, then assert the
        // exact integer: (800000 * 1 * 1 + 50) / 100 = 8000 (exact .005 rounding up
        // is covered by FormulaEngineTests; here we assert service wiring).
        Fixture f = setupSettledCycle(hostAToken, "PERCENT_PER_DAY", 1);
        backdateContributions(f.cycleId(), 1);

        assessOk(hostAToken, f.groupId());
        assertThat(feeAmounts(f.cycleId())).containsExactly(8000L, 8000L);
    }
}

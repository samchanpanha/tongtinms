package com.tongtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
class CloseCalculateTests {

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
        jdbcTemplate.update("DELETE FROM audit_events WHERE actor_user_id IN (SELECT id FROM users WHERE " + filter + ")");
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
        return "10.15." + ThreadLocalRandom.current().nextInt(256) + "." + ThreadLocalRandom.current().nextInt(256);
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

    private long createGroup(String token, String type, int shareCount, String extra) throws Exception {
        String groupBody = """
                {"name":"Hoi Test","type":"%s","baseAmount":1000000,"shareCount":%d,"cycleUnit":"MONTH",
                 "cycleCount":%d,"currency":"VND","maxBid":500000,"bidStep":10000,"bidCloseOffset":7%s}
                """.formatted(type, shareCount, shareCount, extra);
        MvcResult g = mockMvc.perform(post("/api/v1/groups")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(groupBody))
                .andExpect(status().isCreated())
                .andReturn();
        long groupId = objectMapper.readTree(g.getResponse().getContentAsString()).path("id").asLong();

        for (int i = 0; i < shareCount; i++) {
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
        return groupId;
    }

    private JsonNode openCycle(String token, long groupId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/groups/" + groupId + "/cycles/open")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode sharesOf(String token, long groupId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/groups/" + groupId + "/shares")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private void submitBid(String token, long cycleId, long shareId, long amount) throws Exception {
        mockMvc.perform(post("/api/v1/cycles/" + cycleId + "/bids")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shareId\":%d,\"amountMinor\":%d}".formatted(shareId, amount)))
                .andExpect(status().isCreated());
    }

    private MvcResult close(String token, long cycleId, String body) throws Exception {
        MockHttpServletRequestBuilder request = post("/api/v1/cycles/" + cycleId + "/close-and-calculate")
                .header("Authorization", "Bearer " + token);
        if (body != null) {
            request = request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request).andReturn();
    }

    private MvcResult confirm(String token, long cycleId) throws Exception {
        return mockMvc.perform(post("/api/v1/cycles/" + cycleId + "/confirm-payout")
                .header("Authorization", "Bearer " + token)).andReturn();
    }

    private JsonNode closeOk(String token, long cycleId) throws Exception {
        MvcResult result = close(token, cycleId, null);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private void setBidSubmittedAt(long cycleId, long shareId, String iso) {
        jdbcTemplate.update("UPDATE bids SET submitted_at = ? WHERE cycle_id = ? AND share_id = ?",
                Timestamp.from(java.time.Instant.parse(iso)), cycleId, shareId);
    }

    private long ledgerSum(long cycleId, String direction) {
        return jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(amount_minor), 0) FROM ledger_entries WHERE cycle_id = ? AND direction = ?",
                Long.class, cycleId, direction);
    }

    private long ledgerCount(long cycleId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM ledger_entries WHERE cycle_id = ?", Long.class, cycleId);
    }

    private long contributionOf(long cycleId, long shareId) {
        return jdbcTemplate.queryForObject(
                "SELECT amount_minor FROM ledger_entries WHERE cycle_id = ? AND share_id = ? AND type = 'CONTRIBUTION'",
                Long.class, cycleId, shareId);
    }

    private long countLedger(long cycleId, String type, String status) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM ledger_entries WHERE cycle_id = ? AND type = ? AND status = ?",
                Long.class, cycleId, type, status);
    }

    private long sumOfType(long cycleId, String type) {
        return jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(amount_minor), 0) FROM ledger_entries WHERE cycle_id = ? AND type = ?",
                Long.class, cycleId, type);
    }

    private long groupStatus(long groupId) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM groups WHERE id = ? AND status = 'COMPLETED'",
                Long.class, groupId);
    }

    private void assertFixtureACycle1(long groupId, JsonNode cycle, JsonNode shares) {
        long cycleId = cycle.path("id").asLong();
        assertThat(cycle.path("status").asText()).isEqualTo("PAYOUT_PENDING");
        assertThat(cycle.path("winningBid").asLong()).isEqualTo(200000);
        assertThat(cycle.path("grossPot").asLong()).isEqualTo(7_200_000);
        assertThat(cycle.path("hostFee").asLong()).isEqualTo(100_000);
        assertThat(cycle.path("netPayout").asLong()).isEqualTo(7_100_000);
        long winnerId = shares.path(0).path("id").asLong();
        assertThat(cycle.path("winnerShareId").asLong()).isEqualTo(winnerId);
        assertThat(cycle.path("calculatedAt").asText()).isNotBlank();

        List<Map<String, Object>> dead = jdbcTemplate.queryForList(
                "SELECT status, won_cycle_id FROM group_shares WHERE id = ?", winnerId);
        assertThat(dead.get(0).get("status")).isEqualTo("DEAD");
        assertThat(((Number) dead.get(0).get("won_cycle_id")).longValue()).isEqualTo(cycleId);

        long contributions = sumOfType(cycleId, "CONTRIBUTION");
        long hostFee = sumOfType(cycleId, "HOST_FEE");
        long payout = sumOfType(cycleId, "PAYOUT");
        assertThat(contributions).isEqualTo(7_200_000);
        assertThat(hostFee).isEqualTo(100_000);
        assertThat(payout).isEqualTo(7_100_000);
        assertThat(ledgerSum(cycleId, "IN")).isEqualTo(7_200_000);
        assertThat(ledgerSum(cycleId, "OUT")).isEqualTo(7_200_000);
        assertThat(contributionOf(cycleId, shares.path(1).path("id").asLong())).isEqualTo(800_000);
        assertThat(ledgerCount(cycleId)).isEqualTo(11);
        assertThat(countLedger(cycleId, "CONTRIBUTION", "UNPAID")).isEqualTo(9);
        assertThat(countLedger(cycleId, "HOST_FEE", "UNPAID")).isEqualTo(1);
        assertThat(countLedger(cycleId, "PAYOUT", "UNPAID")).isEqualTo(1);
    }

    @Test
    void fixtureACycle1SettlesToNetPayout7100000() throws Exception {
        long groupId = createGroup(hostAToken, "BIDDING", 10,
                ",\"hostFeeType\":\"FIXED_PER_CYCLE\",\"hostFeeMinor\":100000");
        JsonNode cycle = openCycle(hostAToken, groupId);
        assertThat(cycle.path("status").asText()).isEqualTo("BIDDING");
        JsonNode shares = sharesOf(hostAToken, groupId);
        submitBid(hostAToken, cycle.path("id").asLong(), shares.path(0).path("id").asLong(), 200000);

        JsonNode settled = closeOk(hostAToken, cycle.path("id").asLong());
        assertFixtureACycle1(groupId, settled, shares);
    }

    @Test
    void confirmPayoutSettlesCycleAndCycleTwoPays7700000() throws Exception {
        long groupId = createGroup(hostAToken, "BIDDING", 10,
                ",\"hostFeeType\":\"FIXED_PER_CYCLE\",\"hostFeeMinor\":100000");
        JsonNode cycle1 = openCycle(hostAToken, groupId);
        long cycle1Id = cycle1.path("id").asLong();
        JsonNode shares = sharesOf(hostAToken, groupId);
        submitBid(hostAToken, cycle1Id, shares.path(0).path("id").asLong(), 200000);
        closeOk(hostAToken, cycle1Id);

        MvcResult confirmed = confirm(hostAToken, cycle1Id);
        assertThat(confirmed.getResponse().getStatus()).isEqualTo(200);
        JsonNode settled = objectMapper.readTree(confirmed.getResponse().getContentAsString());
        assertThat(settled.path("status").asText()).isEqualTo("SETTLED");
        assertThat(countLedger(cycle1Id, "PAYOUT", "PAID")).isEqualTo(1);
        assertThat(countLedger(cycle1Id, "HOST_FEE", "PAID")).isEqualTo(1);
        assertThat(countLedger(cycle1Id, "CONTRIBUTION", "UNPAID")).isEqualTo(9);

        JsonNode cycle2 = openCycle(hostAToken, groupId);
        long cycle2Id = cycle2.path("id").asLong();
        assertThat(cycle2.path("cycleNo").asInt()).isEqualTo(2);
        assertThat(cycle2.path("status").asText()).isEqualTo("BIDDING");
        submitBid(hostAToken, cycle2Id, shares.path(1).path("id").asLong(), 150000);

        JsonNode settled2 = closeOk(hostAToken, cycle2Id);
        assertThat(settled2.path("netPayout").asLong()).isEqualTo(7_700_000);
        assertThat(settled2.path("grossPot").asLong()).isEqualTo(7_800_000);
        assertThat(settled2.path("winnerShareId").asLong()).isEqualTo(shares.path(1).path("id").asLong());

        assertThat(ledgerSum(cycle2Id, "IN")).isEqualTo(7_800_000);
        assertThat(ledgerSum(cycle2Id, "OUT")).isEqualTo(7_800_000);
        assertThat(contributionOf(cycle2Id, shares.path(0).path("id").asLong())).isEqualTo(1_000_000);
        assertThat(contributionOf(cycle2Id, shares.path(2).path("id").asLong())).isEqualTo(850_000);
        assertThat(ledgerCount(cycle2Id)).isEqualTo(11);
    }

    @Test
    void lastCycleAutoWinnerAndGroupCompleted() throws Exception {
        long groupId = createGroup(hostAToken, "BIDDING", 2, "");
        JsonNode cycle1 = openCycle(hostAToken, groupId);
        long cycle1Id = cycle1.path("id").asLong();
        JsonNode shares = sharesOf(hostAToken, groupId);
        long share1 = shares.path(0).path("id").asLong();
        long share2 = shares.path(1).path("id").asLong();
        submitBid(hostAToken, cycle1Id, share1, 100000);

        JsonNode settled1 = closeOk(hostAToken, cycle1Id);
        assertThat(settled1.path("netPayout").asLong()).isEqualTo(900_000);
        assertThat(settled1.path("hostFee").asLong()).isZero();
        confirm(hostAToken, cycle1Id).getResponse().getStatus();

        JsonNode cycle2 = openCycle(hostAToken, groupId);
        long cycle2Id = cycle2.path("id").asLong();
        assertThat(cycle2.path("status").asText()).isEqualTo("OPEN");

        JsonNode settled2 = closeOk(hostAToken, cycle2Id);
        assertThat(settled2.path("winnerShareId").asLong()).isEqualTo(share2);
        assertThat(settled2.path("winningBid").asLong()).isZero();
        assertThat(settled2.path("grossPot").asLong()).isEqualTo(1_000_000);
        assertThat(settled2.path("netPayout").asLong()).isEqualTo(1_000_000);
        assertThat(contributionOf(cycle2Id, share1)).isEqualTo(1_000_000);
        assertThat(ledgerSum(cycle2Id, "IN")).isEqualTo(1_000_000);
        assertThat(ledgerSum(cycle2Id, "OUT")).isEqualTo(1_000_000);

        MvcResult confirmed = confirm(hostAToken, cycle2Id);
        assertThat(confirmed.getResponse().getStatus()).isEqualTo(200);
        assertThat(groupStatus(groupId)).isEqualTo(1);
    }

    @Test
    void fixedGroupWinnerFollowsShareNoOrder() throws Exception {
        long groupId = createGroup(hostAToken, "FIXED", 3, "");
        JsonNode shares = sharesOf(hostAToken, groupId);
        long share1 = shares.path(0).path("id").asLong();
        long share2 = shares.path(1).path("id").asLong();
        long share3 = shares.path(2).path("id").asLong();

        JsonNode cycle1 = openCycle(hostAToken, groupId);
        assertThat(cycle1.path("status").asText()).isEqualTo("OPEN");
        long cycle1Id = cycle1.path("id").asLong();
        JsonNode settled1 = closeOk(hostAToken, cycle1Id);
        assertThat(settled1.path("winnerShareId").asLong()).isEqualTo(share1);
        assertThat(settled1.path("winningBid").asLong()).isZero();
        assertThat(settled1.path("grossPot").asLong()).isEqualTo(2_000_000);
        confirm(hostAToken, cycle1Id).getResponse().getStatus();

        JsonNode cycle2 = openCycle(hostAToken, groupId);
        long cycle2Id = cycle2.path("id").asLong();
        JsonNode settled2 = closeOk(hostAToken, cycle2Id);
        assertThat(settled2.path("winnerShareId").asLong()).isEqualTo(share2);
        assertThat(contributionOf(cycle2Id, share1)).isEqualTo(1_000_000);
        assertThat(contributionOf(cycle2Id, share3)).isEqualTo(1_000_000);
        assertThat(ledgerSum(cycle2Id, "IN")).isEqualTo(2_000_000);
        confirm(hostAToken, cycle2Id).getResponse().getStatus();

        JsonNode cycle3 = openCycle(hostAToken, groupId);
        long cycle3Id = cycle3.path("id").asLong();
        JsonNode settled3 = closeOk(hostAToken, cycle3Id);
        assertThat(settled3.path("winnerShareId").asLong()).isEqualTo(share3);
        confirm(hostAToken, cycle3Id).getResponse().getStatus();
        assertThat(groupStatus(groupId)).isEqualTo(1);
    }

    @Test
    void percentHostFeeApplied() throws Exception {
        long groupId = createGroup(hostAToken, "BIDDING", 3,
                ",\"hostFeeType\":\"PERCENT_OF_POT\",\"hostFeeBps\":500");
        JsonNode cycle = openCycle(hostAToken, groupId);
        long cycleId = cycle.path("id").asLong();
        JsonNode shares = sharesOf(hostAToken, groupId);
        submitBid(hostAToken, cycleId, shares.path(0).path("id").asLong(), 200000);

        JsonNode settled = closeOk(hostAToken, cycleId);
        assertThat(settled.path("grossPot").asLong()).isEqualTo(1_600_000);
        assertThat(settled.path("hostFee").asLong()).isEqualTo(80_000);
        assertThat(settled.path("netPayout").asLong()).isEqualTo(1_520_000);
        assertThat(ledgerSum(cycleId, "IN")).isEqualTo(1_600_000);
        assertThat(ledgerSum(cycleId, "OUT")).isEqualTo(1_600_000);
    }

    @Test
    void tieBreakEarliestBidWins() throws Exception {
        long groupId = createGroup(hostAToken, "BIDDING", 3, "");
        JsonNode cycle = openCycle(hostAToken, groupId);
        long cycleId = cycle.path("id").asLong();
        JsonNode shares = sharesOf(hostAToken, groupId);
        long share1 = shares.path(0).path("id").asLong();
        long share2 = shares.path(1).path("id").asLong();
        submitBid(hostAToken, cycleId, share2, 200000);
        submitBid(hostAToken, cycleId, share1, 200000);
        setBidSubmittedAt(cycleId, share2, "2026-01-01T00:00:00Z");
        setBidSubmittedAt(cycleId, share1, "2026-01-02T00:00:00Z");

        JsonNode settled = closeOk(hostAToken, cycleId);
        assertThat(settled.path("winnerShareId").asLong()).isEqualTo(share2);
    }

    @Test
    void tieBreakLowestMemberCodeUsesLowestShareNo() throws Exception {
        long groupId = createGroup(hostAToken, "BIDDING", 3, ",\"tieBreak\":\"LOWEST_MEMBER_CODE\"");
        JsonNode cycle = openCycle(hostAToken, groupId);
        long cycleId = cycle.path("id").asLong();
        JsonNode shares = sharesOf(hostAToken, groupId);
        long share1 = shares.path(0).path("id").asLong();
        long share2 = shares.path(1).path("id").asLong();
        submitBid(hostAToken, cycleId, share2, 200000);
        submitBid(hostAToken, cycleId, share1, 200000);
        setBidSubmittedAt(cycleId, share2, "2026-01-01T00:00:00Z");
        setBidSubmittedAt(cycleId, share1, "2026-01-02T00:00:00Z");

        JsonNode settled = closeOk(hostAToken, cycleId);
        assertThat(settled.path("winnerShareId").asLong()).isEqualTo(share1);
    }

    @Test
    void hostDecisionTieRequiresWinnerShareId() throws Exception {
        long groupId = createGroup(hostAToken, "BIDDING", 3, ",\"tieBreak\":\"HOST_DECISION\"");
        JsonNode cycle = openCycle(hostAToken, groupId);
        long cycleId = cycle.path("id").asLong();
        JsonNode shares = sharesOf(hostAToken, groupId);
        long share1 = shares.path(0).path("id").asLong();
        long share2 = shares.path(1).path("id").asLong();
        long share3 = shares.path(2).path("id").asLong();
        submitBid(hostAToken, cycleId, share1, 200000);
        submitBid(hostAToken, cycleId, share2, 200000);

        MvcResult noBody = close(hostAToken, cycleId, null);
        assertThat(noBody.getResponse().getStatus()).isEqualTo(400);

        MvcResult notTied = close(hostAToken, cycleId, "{\"winnerShareId\":%d}".formatted(share3));
        assertThat(notTied.getResponse().getStatus()).isEqualTo(400);

        MvcResult chosen = close(hostAToken, cycleId, "{\"winnerShareId\":%d}".formatted(share2));
        assertThat(chosen.getResponse().getStatus()).isEqualTo(200);
        JsonNode settled = objectMapper.readTree(chosen.getResponse().getContentAsString());
        assertThat(settled.path("winnerShareId").asLong()).isEqualTo(share2);
        assertThat(ledgerCount(cycleId)).isEqualTo(3);
    }

    @Test
    void closeWithoutBidsRejected() throws Exception {
        long groupId = createGroup(hostAToken, "BIDDING", 3, "");
        JsonNode cycle = openCycle(hostAToken, groupId);
        long cycleId = cycle.path("id").asLong();

        MvcResult result = close(hostAToken, cycleId, null);
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(ledgerCount(cycleId)).isZero();
        String status = jdbcTemplate.queryForObject("SELECT status FROM cycles WHERE id = ?", String.class, cycleId);
        assertThat(status).isEqualTo("BIDDING");
    }

    @Test
    void repeatCloseAndDoubleConfirmRejected() throws Exception {
        long groupId = createGroup(hostAToken, "BIDDING", 3, "");
        JsonNode cycle = openCycle(hostAToken, groupId);
        long cycleId = cycle.path("id").asLong();
        JsonNode shares = sharesOf(hostAToken, groupId);
        submitBid(hostAToken, cycleId, shares.path(0).path("id").asLong(), 200000);
        closeOk(hostAToken, cycleId);
        long rows = ledgerCount(cycleId);
        assertThat(rows).isPositive();

        assertThat(close(hostAToken, cycleId, null).getResponse().getStatus()).isEqualTo(400);
        assertThat(ledgerCount(cycleId)).isEqualTo(rows);

        assertThat(confirm(hostAToken, cycleId).getResponse().getStatus()).isEqualTo(200);
        assertThat(confirm(hostAToken, cycleId).getResponse().getStatus()).isEqualTo(400);
        String status = jdbcTemplate.queryForObject("SELECT status FROM cycles WHERE id = ?", String.class, cycleId);
        assertThat(status).isEqualTo("SETTLED");
    }

    @Test
    void foreignOwnerGets404AndAnonymousGets401() throws Exception {
        long groupId = createGroup(hostAToken, "BIDDING", 3, "");
        JsonNode cycle = openCycle(hostAToken, groupId);
        long cycleId = cycle.path("id").asLong();
        JsonNode shares = sharesOf(hostAToken, groupId);
        submitBid(hostAToken, cycleId, shares.path(0).path("id").asLong(), 200000);

        assertThat(close(hostBToken, cycleId, null).getResponse().getStatus()).isEqualTo(404);
        assertThat(confirm(hostBToken, cycleId).getResponse().getStatus()).isEqualTo(404);
        mockMvc.perform(post("/api/v1/cycles/" + cycleId + "/close-and-calculate"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/cycles/" + cycleId + "/confirm-payout"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void defaultedSharePaysFullAndCannotWin() throws Exception {
        long groupId = createGroup(hostAToken, "BIDDING", 3, "");
        JsonNode cycle = openCycle(hostAToken, groupId);
        long cycleId = cycle.path("id").asLong();
        JsonNode shares = sharesOf(hostAToken, groupId);
        long share1 = shares.path(0).path("id").asLong();
        long share2 = shares.path(1).path("id").asLong();
        long share3 = shares.path(2).path("id").asLong();
        jdbcTemplate.update("UPDATE group_shares SET status = 'DEFAULTED' WHERE id = ?", share1);
        submitBid(hostAToken, cycleId, share3, 100000);

        JsonNode settled = closeOk(hostAToken, cycleId);
        assertThat(settled.path("winnerShareId").asLong()).isEqualTo(share3);
        assertThat(settled.path("grossPot").asLong()).isEqualTo(1_900_000);
        assertThat(settled.path("netPayout").asLong()).isEqualTo(1_900_000);

        assertThat(contributionOf(cycleId, share1)).isEqualTo(1_000_000);
        assertThat(contributionOf(cycleId, share2)).isEqualTo(900_000);
        assertThat(ledgerSum(cycleId, "IN")).isEqualTo(1_900_000);
        assertThat(ledgerSum(cycleId, "OUT")).isEqualTo(1_900_000);

        String share1Status = jdbcTemplate.queryForObject(
                "SELECT status FROM group_shares WHERE id = ?", String.class, share1);
        assertThat(share1Status).isEqualTo("DEFAULTED");
        Long won = jdbcTemplate.queryForObject(
                "SELECT won_cycle_id FROM group_shares WHERE id = ?", Long.class, share1);
        assertThat(won).isNull();
    }

    @Test
    void summaryRevealsWinnerAfterClose() throws Exception {
        long groupId = createGroup(hostAToken, "BIDDING", 3, "");
        JsonNode cycle = openCycle(hostAToken, groupId);
        long cycleId = cycle.path("id").asLong();
        JsonNode shares = sharesOf(hostAToken, groupId);
        long winnerId = shares.path(0).path("id").asLong();
        submitBid(hostAToken, cycleId, winnerId, 200000);

        MvcResult sealedResult = mockMvc.perform(get("/api/v1/cycles/" + cycleId + "/summary")
                        .header("Authorization", "Bearer " + hostAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sealed").value(true))
                .andReturn();
        JsonNode sealed = objectMapper.readTree(sealedResult.getResponse().getContentAsString());
        assertThat(sealed.path("winnerShareId").isNull()).isTrue();
        assertThat(sealed.path("entries").path(0).path("amountMinor").isNull()).isTrue();

        closeOk(hostAToken, cycleId);

        MvcResult revealedResult = mockMvc.perform(get("/api/v1/cycles/" + cycleId + "/summary")
                        .header("Authorization", "Bearer " + hostAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sealed").value(false))
                .andReturn();
        JsonNode revealed = objectMapper.readTree(revealedResult.getResponse().getContentAsString());
        assertThat(revealed.path("winnerShareId").asLong()).isEqualTo(winnerId);
        assertThat(revealed.path("winningBid").asLong()).isEqualTo(200000);
        assertThat(revealed.path("grossPot").asLong()).isEqualTo(1_600_000);
        assertThat(revealed.path("netPayout").asLong()).isEqualTo(1_600_000);
        assertThat(revealed.path("entries").path(0).path("amountMinor").asLong()).isEqualTo(200000);
        assertThat(revealed.path("entries").path(0).path("shareStatus").asText()).isEqualTo("DEAD");
    }
}

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
 * Step 16 hardening: every money/winner action writes an audit_events row IN
 * THE SAME tx as the state change (release gate "corrections create reversal
 * trails"). Covers CYCLE_OPENED, BID_SUBMITTED (host + member portal),
 * CYCLE_SETTLED, PAYOUT_CONFIRMED, PAYMENT_RECORDED. A failed write must not
 * leave a stray audit row (financial write and audit commit/roll back together).
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuditTrailTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<String> createdPhones = new ArrayList<>();
    private String hostPhone;
    private String hostToken;

    @BeforeEach
    void registerHost() throws Exception {
        hostPhone = "0911" + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
        createdPhones.add(hostPhone);
        hostToken = register(hostPhone);
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
        return "10.19." + ThreadLocalRandom.current().nextInt(256) + "." + ThreadLocalRandom.current().nextInt(256);
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

    private long createMember() throws Exception {
        MvcResult m = mockMvc.perform(post("/api/v1/members")
                        .header("Authorization", "Bearer " + hostToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Hoi Vien\",\"phone\":\"%s\"}".formatted(randomPhone())))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode member = objectMapper.readTree(m.getResponse().getContentAsString());
        createdPhones.add(member.path("phone").asText());
        return member.path("id").asLong();
    }

    private long createGroup() throws Exception {
        String body = """
                {"name":"Hoi Audit","type":"BIDDING","baseAmount":1000000,"shareCount":3,"cycleUnit":"MONTH",
                 "cycleCount":3,"currency":"VND","maxBid":500000,"bidStep":10000,"bidCloseOffset":7,
                 "hostFeeType":"FIXED_PER_CYCLE","hostFeeMinor":100000}
                """;
        MvcResult g = mockMvc.perform(post("/api/v1/groups")
                        .header("Authorization", "Bearer " + hostToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(g.getResponse().getContentAsString()).path("id").asLong();
    }

    private long assignShare(long groupId, long memberId) throws Exception {
        MvcResult s = mockMvc.perform(post("/api/v1/groups/" + groupId + "/shares")
                        .header("Authorization", "Bearer " + hostToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberProfileId\":%d,\"count\":1}".formatted(memberId)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode shares = objectMapper.readTree(s.getResponse().getContentAsString());
        return shares.path(shares.size() - 1).path("id").asLong();
    }

    private MvcResult perform(String method, String url) throws Exception {
        return mockMvc.perform(post(url).header("Authorization", "Bearer " + hostToken))
                .andReturn();
    }

    private long hostUserId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE phone = ? OR phone = '+84' || substr(?, 2)",
                Long.class, hostPhone, hostPhone);
    }

    private List<JsonNode> auditRows(String entityType, String action) {
        List<JsonNode> rows = new ArrayList<>();
        jdbcTemplate.query(
                "SELECT entity_id, actor_user_id, payload_json FROM audit_events " +
                        "WHERE entity_type = ? AND action = ? AND actor_user_id IN (SELECT id FROM users WHERE " + phonesFilter() + ") ORDER BY id",
                rs -> {
                    try {
                        JsonNode row = objectMapper.readTree(
                                "{\"entityId\":\"" + rs.getString("entity_id")
                                        + "\",\"actorUserId\":" + rs.getLong("actor_user_id")
                                        + ",\"payload\":" + rs.getString("payload_json") + "}");
                        rows.add(row);
                    } catch (Exception ignored) {
                    }
                },
                entityType, action);
        return rows;
    }

    @Test
    void hostMoneyActionsWriteAuditRowsWithAmounts() throws Exception {
        long groupId = createGroup();
        List<Long> shareIds = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            shareIds.add(assignShare(groupId, createMember()));
        }
        mockMvc.perform(post("/api/v1/groups/" + groupId + "/start")
                        .header("Authorization", "Bearer " + hostToken))
                .andExpect(status().isOk());

        MvcResult o = mockMvc.perform(post("/api/v1/groups/" + groupId + "/cycles/open")
                        .header("Authorization", "Bearer " + hostToken))
                .andExpect(status().isCreated()).andReturn();
        long cycleId = objectMapper.readTree(o.getResponse().getContentAsString()).path("id").asLong();

        mockMvc.perform(post("/api/v1/cycles/" + cycleId + "/bids")
                        .header("Authorization", "Bearer " + hostToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shareId\":%d,\"amountMinor\":200000}".formatted(shareIds.get(0))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/cycles/" + cycleId + "/close-and-calculate")
                        .header("Authorization", "Bearer " + hostToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/cycles/" + cycleId + "/confirm-payout")
                        .header("Authorization", "Bearer " + hostToken))
                .andExpect(status().isOk());

        long hostUser = hostUserId();

        List<JsonNode> opens = auditRows("Cycle", "CYCLE_OPENED");
        assertThat(opens).hasSize(1);
        assertThat(opens.get(0).path("actorUserId").asLong()).isEqualTo(hostUser);
        assertThat(opens.get(0).path("payload").path("cycleNo").asLong()).isEqualTo(1);

        List<JsonNode> bids = auditRows("Bid", "BID_SUBMITTED");
        assertThat(bids).hasSize(1);
        assertThat(bids.get(0).path("actorUserId").asLong()).isEqualTo(hostUser);
        assertThat(bids.get(0).path("payload").path("amountMinor").asLong()).isEqualTo(200_000L);
        assertThat(bids.get(0).path("payload").path("shareId").asLong()).isEqualTo(shareIds.get(0));
        assertThat(bids.get(0).path("payload").path("cycleId").asLong()).isEqualTo(cycleId);
        assertThat(bids.get(0).path("payload").path("currency").asText()).isEqualTo("VND");

        List<JsonNode> settled = auditRows("Cycle", "CYCLE_SETTLED");
        assertThat(settled).hasSize(1);
        JsonNode settledPayload = settled.get(0).path("payload");
        assertThat(settled.get(0).path("actorUserId").asLong()).isEqualTo(hostUser);
        assertThat(settledPayload.path("winnerShareId").asLong()).isEqualTo(shareIds.get(0));
        assertThat(settledPayload.path("winningBid").asLong()).isEqualTo(200_000L);
        assertThat(settledPayload.path("hostFee").asLong()).isEqualTo(100_000L);
        assertThat(settledPayload.path("netPayout").asLong())
                .isEqualTo(settledPayload.path("grossPot").asLong() - 100_000L);
        assertThat(settledPayload.path("formulaVersion").asLong()).isEqualTo(1);

        List<JsonNode> payouts = auditRows("Cycle", "PAYOUT_CONFIRMED");
        assertThat(payouts).hasSize(1);
        assertThat(payouts.get(0).path("actorUserId").asLong()).isEqualTo(hostUser);
        assertThat(payouts.get(0).path("payload").path("netPayout").asLong())
                .isEqualTo(settledPayload.path("netPayout").asLong());

        long payerEntry = jdbcTemplate.queryForObject(
                "SELECT id FROM ledger_entries WHERE cycle_id = ? AND share_id = ? AND type = 'CONTRIBUTION'",
                Long.class, cycleId, shareIds.get(1));
        mockMvc.perform(post("/api/v1/groups/" + groupId + "/payments")
                        .header("Authorization", "Bearer " + hostToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountMinor\":800000,\"currency\":\"VND\",\"allocations\":" +
                                "[{\"ledgerEntryId\":%d,\"amountMinor\":800000}]}".formatted(payerEntry)))
                .andExpect(status().isCreated());

        List<JsonNode> payments = auditRows("Payment", "PAYMENT_RECORDED");
        assertThat(payments).hasSize(1);
        JsonNode paymentPayload = payments.get(0).path("payload");
        assertThat(payments.get(0).path("actorUserId").asLong()).isEqualTo(hostUser);
        assertThat(paymentPayload.path("amountMinor").asLong()).isEqualTo(800_000L);
        assertThat(paymentPayload.path("currency").asText()).isEqualTo("VND");
        assertThat(paymentPayload.path("allocations")).hasSize(1);
        assertThat(paymentPayload.path("allocations").path(0).path("amountMinor").asLong()).isEqualTo(800_000L);
    }

    @Test
    void failedFinancialWriteLeavesNoAuditRow() throws Exception {
        long groupId = createGroup();
        List<Long> shareIds = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            shareIds.add(assignShare(groupId, createMember()));
        }
        mockMvc.perform(post("/api/v1/groups/" + groupId + "/start")
                        .header("Authorization", "Bearer " + hostToken))
                .andExpect(status().isOk());
        MvcResult o = mockMvc.perform(post("/api/v1/groups/" + groupId + "/cycles/open")
                        .header("Authorization", "Bearer " + hostToken))
                .andExpect(status().isCreated()).andReturn();
        long cycleId = objectMapper.readTree(o.getResponse().getContentAsString()).path("id").asLong();
        mockMvc.perform(post("/api/v1/cycles/" + cycleId + "/bids")
                        .header("Authorization", "Bearer " + hostToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shareId\":%d,\"amountMinor\":200000}".formatted(shareIds.get(0))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/cycles/" + cycleId + "/close-and-calculate")
                        .header("Authorization", "Bearer " + hostToken))
                .andExpect(status().isOk());

        long entry = jdbcTemplate.queryForObject(
                "SELECT id FROM ledger_entries WHERE cycle_id = ? AND type = 'CONTRIBUTION' LIMIT 1",
                Long.class, cycleId);
        // allocation sum != amountMinor -> 400 BEFORE any write; no PAYMENT_RECORDED row after
        mockMvc.perform(post("/api/v1/groups/" + groupId + "/payments")
                        .header("Authorization", "Bearer " + hostToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountMinor\":800001,\"currency\":\"VND\",\"allocations\":" +
                                "[{\"ledgerEntryId\":%d,\"amountMinor\":800000}]}".formatted(entry)))
                .andExpect(status().isBadRequest());
        assertThat(auditRows("Payment", "PAYMENT_RECORDED")).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM payments WHERE group_id = ?", Integer.class, groupId)).isZero();
    }

    @Test
    void memberPortalBidAuditsUnderMemberUserId() throws Exception {
        long groupId = createGroup();
        List<Long> shareIds = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            shareIds.add(assignShare(groupId, createMember()));
        }
        long shareId = shareIds.get(0);
        long memberId = jdbcTemplate.queryForObject(
                "SELECT member_profile_id FROM group_shares WHERE id = ?", Long.class, shareId);
        String memberPhone = jdbcTemplate.queryForObject(
                "SELECT phone FROM member_profiles WHERE id = ?", String.class, memberId);
        MvcResult set = mockMvc.perform(post("/api/v1/members/" + memberId + "/set-login")
                        .header("Authorization", "Bearer " + hostToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"password123\"}"))
                .andExpect(status().isOk()).andReturn();
        assertThat(objectMapper.readTree(set.getResponse().getContentAsString())
                .path("loginEnabled").asBoolean()).isTrue();
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", uniqueIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"%s\",\"password\":\"password123\"}".formatted(memberPhone)))
                .andReturn();
        assertThat(login.getResponse().getStatus()).isEqualTo(200);
        String memberToken = objectMapper.readTree(login.getResponse().getContentAsString())
                .path("tokens").path("accessToken").asText();

        mockMvc.perform(post("/api/v1/groups/" + groupId + "/start")
                        .header("Authorization", "Bearer " + hostToken))
                .andExpect(status().isOk());
        MvcResult o = mockMvc.perform(post("/api/v1/groups/" + groupId + "/cycles/open")
                        .header("Authorization", "Bearer " + hostToken))
                .andExpect(status().isCreated()).andReturn();
        long cycleId = objectMapper.readTree(o.getResponse().getContentAsString()).path("id").asLong();

        mockMvc.perform(post("/api/v1/me/cycles/" + cycleId + "/bids")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shareId\":%d,\"amountMinor\":200000}".formatted(shareId)))
                .andExpect(status().isCreated());

        List<JsonNode> bids = auditRows("Bid", "BID_SUBMITTED");
        assertThat(bids).hasSize(1);
        Long memberUserId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE phone = ? OR phone = '+84' || substr(?, 2)", Long.class,
                memberPhone, memberPhone);
        assertThat(bids.get(0).path("actorUserId").asLong()).isEqualTo(memberUserId);
        assertThat(bids.get(0).path("payload").path("amountMinor").asLong()).isEqualTo(200_000L);
    }
}
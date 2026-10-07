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
 * Step 16 hardening: POST /groups/{id}/payments idempotency (release gate
 * "retrying never duplicates money"). Same key + identical payload replays the
 * original payment (200, no new rows); same key + different payload -> 409;
 * the key is scoped per group; without a key every call creates a new payment.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PaymentIdempotencyTests {

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
                {"name":"Hoi Idempotency","type":"BIDDING","baseAmount":1000000,"shareCount":3,"cycleUnit":"MONTH",
                 "cycleCount":3,"currency":"VND","maxBid":500000,"bidStep":10000,"bidCloseOffset":7}
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

    private long settledGroup() throws Exception {
        long groupId = createGroup();
        for (int i = 0; i < 3; i++) {
            assignShare(groupId, createMember());
        }
        mockMvc.perform(post("/api/v1/groups/" + groupId + "/start")
                        .header("Authorization", "Bearer " + hostToken))
                .andExpect(status().isOk());
        MvcResult o = mockMvc.perform(post("/api/v1/groups/" + groupId + "/cycles/open")
                        .header("Authorization", "Bearer " + hostToken))
                .andExpect(status().isCreated()).andReturn();
        long cycleId = objectMapper.readTree(o.getResponse().getContentAsString()).path("id").asLong();
        MvcResult s = mockMvc.perform(get("/api/v1/groups/" + groupId + "/shares")
                        .header("Authorization", "Bearer " + hostToken))
                .andExpect(status().isOk()).andReturn();
        JsonNode shares = objectMapper.readTree(s.getResponse().getContentAsString());
        long firstShare = shares.path(0).path("id").asLong();
        mockMvc.perform(post("/api/v1/cycles/" + cycleId + "/bids")
                        .header("Authorization", "Bearer " + hostToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shareId\":%d,\"amountMinor\":200000}".formatted(firstShare)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/cycles/" + cycleId + "/close-and-calculate")
                        .header("Authorization", "Bearer " + hostToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/cycles/" + cycleId + "/confirm-payout")
                        .header("Authorization", "Bearer " + hostToken))
                .andExpect(status().isOk());
        return groupId;
    }

    private long unpaidEntry(long groupId) throws Exception {
        MvcResult l = mockMvc.perform(get("/api/v1/groups/" + groupId + "/ledger")
                        .header("Authorization", "Bearer " + hostToken))
                .andExpect(status().isOk()).andReturn();
        JsonNode ledger = objectMapper.readTree(l.getResponse().getContentAsString());
        for (JsonNode e : ledger.path("entries")) {
            if ("UNPAID".equals(e.path("status").asText())) {
                return e.path("entryId").asLong();
            }
        }
        throw new IllegalStateException("no unpaid ledger entry found");
    }

    private static String payBody(long entryId, long amountMinor) {
        return ("{\"amountMinor\":%d,\"currency\":\"VND\",\"allocations\":" +
                "[{\"ledgerEntryId\":%d,\"amountMinor\":%d}]}").formatted(amountMinor, entryId, amountMinor);
    }

    @Test
    void sameKeyIdenticalPayloadReplaysOriginalWithoutNewRows() throws Exception {
        long groupId = settledGroup();
        long entryId = unpaidEntry(groupId);

        MvcResult first = mockMvc.perform(post("/api/v1/groups/" + groupId + "/payments")
                        .header("Authorization", "Bearer " + hostToken)
                        .header("Idempotency-Key", "replay-me-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody(entryId, 800000)))
                .andExpect(status().isCreated()).andReturn();
        long firstId = objectMapper.readTree(first.getResponse().getContentAsString()).path("id").asLong();
        int allocationsAfterFirst = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM payment_allocations WHERE payment_id = ?", Integer.class, firstId);

        MvcResult replay = mockMvc.perform(post("/api/v1/groups/" + groupId + "/payments")
                        .header("Authorization", "Bearer " + hostToken)
                        .header("Idempotency-Key", "replay-me-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody(entryId, 800000)))
                .andExpect(status().isOk()).andReturn();
        long replayId = objectMapper.readTree(replay.getResponse().getContentAsString()).path("id").asLong();
        assertThat(replayId).isEqualTo(firstId);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM payment_allocations WHERE payment_id = ?", Integer.class, firstId))
                .isEqualTo(allocationsAfterFirst);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM payments WHERE group_id = ?", Integer.class, groupId)).isEqualTo(1);
    }

    @Test
    void sameKeyDifferentPayloadConflicts() throws Exception {
        long groupId = settledGroup();
        long entryId = unpaidEntry(groupId);

        mockMvc.perform(post("/api/v1/groups/" + groupId + "/payments")
                        .header("Authorization", "Bearer " + hostToken)
                        .header("Idempotency-Key", "conflict-me-002")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody(entryId, 800000)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/groups/" + groupId + "/payments")
                        .header("Authorization", "Bearer " + hostToken)
                        .header("Idempotency-Key", "conflict-me-002")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody(entryId, 700000)))
                .andExpect(status().isConflict());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM payments WHERE group_id = ?", Integer.class, groupId)).isEqualTo(1);
    }

    @Test
    void sameKeyAllowedOnDifferentGroups() throws Exception {
        long groupA = settledGroup();
        long groupB = settledGroup();
        String key = "shared-key-003";
        mockMvc.perform(post("/api/v1/groups/" + groupA + "/payments")
                        .header("Authorization", "Bearer " + hostToken)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody(unpaidEntry(groupA), 800000)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/groups/" + groupB + "/payments")
                        .header("Authorization", "Bearer " + hostToken)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody(unpaidEntry(groupB), 800000)))
                .andExpect(status().isCreated());
    }

    @Test
    void withoutKeyEveryCallCreatesNewPayment() throws Exception {
        long groupId = settledGroup();
        long first = unpaidEntry(groupId);
        MvcResult p1 = mockMvc.perform(post("/api/v1/groups/" + groupId + "/payments")
                        .header("Authorization", "Bearer " + hostToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody(first, 800000)))
                .andExpect(status().isCreated()).andReturn();
        long second = unpaidEntry(groupId); // next unpaid entry
        MvcResult p2 = mockMvc.perform(post("/api/v1/groups/" + groupId + "/payments")
                        .header("Authorization", "Bearer " + hostToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody(second, 800000)))
                .andExpect(status().isCreated()).andReturn();
        assertThat(objectMapper.readTree(p1.getResponse().getContentAsString()).path("id").asLong())
                .isNotEqualTo(objectMapper.readTree(p2.getResponse().getContentAsString()).path("id").asLong());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM payments WHERE group_id = ?", Integer.class, groupId)).isEqualTo(2);
    }

    @Test
    void oversizedKeyRejected() throws Exception {
        long groupId = settledGroup();
        long entryId = unpaidEntry(groupId);
        String tooLong = "k".repeat(65);
        mockMvc.perform(post("/api/v1/groups/" + groupId + "/payments")
                        .header("Authorization", "Bearer " + hostToken)
                        .header("Idempotency-Key", tooLong)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payBody(entryId, 800000)))
                .andExpect(status().isBadRequest());
    }
}
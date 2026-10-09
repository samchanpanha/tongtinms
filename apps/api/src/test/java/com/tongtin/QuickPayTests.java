package com.tongtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Step 36: quick-pay — host pays a member's total in one step; the backend
 * auto-allocates oldest-due-first (then entry id) across the member's
 * outstanding direction-IN obligations incl. LATE_FEE by default, runs the SAME
 * core path as POST /payments, and the result is a normal payment row.
 *
 * CONTRACT (02-ARCHITECTURE §5 Quick-pay, 01-DOMAIN §18):
 *  - pool works on UNPAID/PARTIAL, direction 'IN' (incl. LATE_FEE unless opt-out)
 *  - allocations sum exactly to amountMinor; amount > total remaining -> 400
 *  - member not in group -> 404; nothing outstanding -> 400
 *  - idempotency replay returns the original (200); different payload -> 409
 *  - quick-pay is host-only; receipt attach stays POST /payments/{id}/attachments
 */
@SpringBootTest
@AutoConfigureMockMvc
class QuickPayTests {

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
        jdbcTemplate.update("DELETE FROM attachments WHERE owner_id IN (SELECT id FROM owner_accounts WHERE user_id IN (SELECT id FROM users WHERE " + filter + "))");
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
        return "10.36." + ThreadLocalRandom.current().nextInt(256) + "." + ThreadLocalRandom.current().nextInt(256);
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

    /** 3-share group, cycleCount 3; late fees optional. */
    private long createGroup(String token, String lateFeeType, long lateFeeValue) throws Exception {
        String groupBody = """
                {"name":"Hoi Quick Pay","type":"BIDDING","baseAmount":1000000,"shareCount":3,"cycleUnit":"MONTH",
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
            MvcResult m = mockMvc.perform(post("/api/v1/members")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"fullName\":\"TV %d\",\"phone\":\"%s\"}".formatted(i, randomPhone())))
                    .andExpect(status().isCreated())
                    .andReturn();
            long memberId = objectMapper.readTree(m.getResponse().getContentAsString()).path("id").asLong();
            mockMvc.perform(post("/api/v1/groups/" + groupId + "/shares")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"memberProfileId\":%d,\"count\":1}".formatted(memberId)))
                    .andExpect(status().isCreated());
        }
        mockMvc.perform(post("/api/v1/groups/" + groupId + "/start")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        return groupId;
    }

    /** Opens, bids (given share index wins), closes and confirms one cycle. Returns cycle id. */
    private long settleCycle(String token, long groupId, int shareIndex) throws Exception {
        JsonNode shares = sharesOf(token, groupId);
        MvcResult open = mockMvc.perform(post("/api/v1/groups/" + groupId + "/cycles/open")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();
        long cycleId = objectMapper.readTree(open.getResponse().getContentAsString()).path("id").asLong();
        mockMvc.perform(post("/api/v1/cycles/" + cycleId + "/bids")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shareId\":%d,\"amountMinor\":200000}".formatted(shares.path(shareIndex).path("id").asLong())))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/cycles/" + cycleId + "/close-and-calculate")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/cycles/" + cycleId + "/confirm-payout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        return cycleId;
    }

    private long settleCycle(String token, long groupId) throws Exception {
        return settleCycle(token, groupId, 0);
    }

    private JsonNode sharesOf(String token, long groupId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/groups/" + groupId + "/shares")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private long contributionOf(String token, long groupId, int index) throws Exception {
        JsonNode shares = sharesOf(token, groupId);
        long memberProfileId = shares.path(index).path("memberProfileId").asLong();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, member_profile_id FROM ledger_entries WHERE group_id = ? AND type = 'CONTRIBUTION'"
                        + " AND direction = 'IN' AND member_profile_id = ? ORDER BY due_at, id",
                groupId, memberProfileId);
        assertThat(rows).isNotEmpty();
        return ((Number) rows.get(0).get("id")).longValue();
    }

    private long lateFeeOf(long groupId, long sourceEntryId) {
        Long feeId = jdbcTemplate.queryForObject(
                "SELECT id FROM ledger_entries WHERE group_id = ? AND type = 'LATE_FEE'"
                        + " AND cycle_id = (SELECT cycle_id FROM ledger_entries WHERE id = ?)"
                        + " AND share_id = (SELECT share_id FROM ledger_entries WHERE id = ?) ORDER BY id LIMIT 1",
                Long.class, groupId, sourceEntryId, sourceEntryId);
        return feeId;
    }

    private JsonNode quickPay(String token, long groupId, String body) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/groups/" + groupId + "/quick-pay")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private int quickPayStatus(String token, long groupId, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/groups/" + groupId + "/quick-pay")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn().getResponse().getStatus();
    }

    private String entryStatus(long entryId) {
        return jdbcTemplate.queryForObject("SELECT status FROM ledger_entries WHERE id = ?", String.class, entryId);
    }

    private long paymentCount(long groupId) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM payments WHERE group_id = ?", Long.class, groupId);
    }

    private void backdateContributions(long cycleId, int days) {
        jdbcTemplate.update(
                "UPDATE ledger_entries SET due_at = now() - interval '" + days + " days' "
                        + "WHERE cycle_id = ? AND type = 'CONTRIBUTION'", cycleId);
    }

    private void assessFees(String token, long groupId) throws Exception {
        mockMvc.perform(post("/api/v1/groups/" + groupId + "/late-fees/assess")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private long memberProfileIdOf(long groupId, long contributionId) {
        return jdbcTemplate.queryForObject(
                "SELECT member_profile_id FROM ledger_entries WHERE id = ?", Long.class, contributionId);
    }

    @Test
    void quickPayAutoAllocatesOldestFirstIncludingLateFee() throws Exception {
        long groupId = createGroup(hostAToken, "FIXED", 20000);
        long cycleId = settleCycle(hostAToken, groupId);
        backdateContributions(cycleId, 3);
        assessFees(hostAToken, groupId);

        long contribution = contributionOf(hostAToken, groupId, 1);
        long fee = lateFeeOf(groupId, contribution);
        long memberId = memberProfileIdOf(groupId, contribution);

        JsonNode response = quickPay(hostAToken, groupId, """
                {"memberProfileId":%d,"amountMinor":820000,"currency":"VND","method":"CASH",
                 "note":"tru tien ca goc lan phat"}
                """.formatted(memberId));
        assertThat(response.path("amountMinor").asLong()).isEqualTo(820_000);
        assertThat(response.path("method").asText()).isEqualTo("CASH");
        assertThat(response.path("note").asText()).isEqualTo("tru tien ca goc lan phat");
        JsonNode allocations = response.path("allocations");
        assertThat(allocations).hasSize(2);
        assertThat(allocations.path(0).path("ledgerEntryId").asLong()).isEqualTo(contribution);
        assertThat(allocations.path(0).path("amountMinor").asLong()).isEqualTo(800_000);
        assertThat(allocations.path(1).path("ledgerEntryId").asLong()).isEqualTo(fee);
        assertThat(allocations.path(1).path("amountMinor").asLong()).isEqualTo(20_000);

        // oldest-first keeps the contribution (same due_at, lower entry id) ahead of its fee
        assertThat(entryStatus(contribution)).isEqualTo("PAID");
        assertThat(entryStatus(fee)).isEqualTo("PAID");
        assertThat(paymentCount(groupId)).isEqualTo(1);

        // only the OTHER member's debts remain
        long otherMember = memberProfileIdOf(groupId, contributionOf(hostAToken, groupId, 2));
        MvcResult debts = mockMvc.perform(get("/api/v1/groups/" + groupId + "/debts")
                        .header("Authorization", "Bearer " + hostAToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode debtList = objectMapper.readTree(debts.getResponse().getContentAsString());
        assertThat(debtList).hasSize(2);
        for (JsonNode d : debtList) {
            assertThat(d.path("memberProfileId").asLong()).isNotEqualTo(memberId);
            assertThat(d.path("memberProfileId").asLong()).isEqualTo(otherMember);
        }

        // audit marks the auto-allocation origin
        List<JsonNode> recent = jdbcTemplate.queryForList(
                        "SELECT payload_json FROM audit_events WHERE action = 'PAYMENT_RECORDED' AND entity_type = 'Payment'"
                                + " AND actor_user_id IN (SELECT id FROM users WHERE " + ownerFilter() + ")")
                .stream()
                .map(row -> (String) row.get("payload_json"))
                .map(s -> {
                    try {
                        return objectMapper.readTree(s);
                    } catch (Exception ex) {
                        throw new RuntimeException(ex);
                    }
                })
                .toList();
        assertThat(recent).hasSize(1);
        assertThat(recent.get(0).path("autoAllocated").asBoolean()).isTrue();
        assertThat(recent.get(0).path("amountMinor").asLong()).isEqualTo(820_000);
    }

    @Test
    void quickPayCanOptOutOfLateFees() throws Exception {
        long groupId = createGroup(hostAToken, "FIXED", 20000);
        long cycleId = settleCycle(hostAToken, groupId);
        backdateContributions(cycleId, 3);
        assessFees(hostAToken, groupId);

        long contribution = contributionOf(hostAToken, groupId, 1);
        long fee = lateFeeOf(groupId, contribution);
        long memberId = memberProfileIdOf(groupId, contribution);

        JsonNode response = quickPay(hostAToken, groupId, """
                {"memberProfileId":%d,"amountMinor":800000,"currency":"VND","allocateLateFees":false}
                """.formatted(memberId));
        assertThat(response.path("allocations")).hasSize(1);
        assertThat(response.path("allocations").path(0).path("ledgerEntryId").asLong()).isEqualTo(contribution);
        assertThat(entryStatus(contribution)).isEqualTo("PAID");
        assertThat(entryStatus(fee)).isEqualTo("UNPAID");
    }

    @Test
    void quickPayPartialCoversOldestObligationFirst() throws Exception {
        long groupId = createGroup(hostAToken, "NONE", 0);
        long cycle1 = settleCycle(hostAToken, groupId, 0);
        // cycle 2: share 3 wins, so member 2 (share 2) owes a second contribution
        settleCycle(hostAToken, groupId, 2);
        backdateContributions(cycle1, 5);

        long oldContribution = contributionOf(hostAToken, groupId, 1);
        long memberId = memberProfileIdOf(groupId, oldContribution);
        List<Map<String, Object>> member2Contribs = jdbcTemplate.queryForList(
                "SELECT id, cycle_id, due_at FROM ledger_entries WHERE group_id = ? AND type = 'CONTRIBUTION'"
                        + " AND member_profile_id = ? ORDER BY due_at, id",
                groupId, memberId);
        assertThat(member2Contribs).hasSize(2);
        long newerEntry = ((Number) member2Contribs.get(1).get("id")).longValue();
        assertThat(newerEntry).isNotEqualTo(oldContribution);
        assertThat(entryStatus(oldContribution)).isEqualTo("UNPAID");
        assertThat(entryStatus(newerEntry)).isEqualTo("UNPAID");

        // enough only for the oldest obligation (cycle 1 contribution)
        JsonNode response = quickPay(hostAToken, groupId, """
                {"memberProfileId":%d,"amountMinor":800000,"currency":"VND"}
                """.formatted(memberId));
        assertThat(response.path("allocations")).hasSize(1);
        assertThat(response.path("allocations").path(0).path("ledgerEntryId").asLong()).isEqualTo(oldContribution);
        assertThat(entryStatus(oldContribution)).isEqualTo("PAID");
        assertThat(entryStatus(newerEntry)).isEqualTo("UNPAID");
    }

    @Test
    void quickPayPartialSplitsOneObligation() throws Exception {
        long groupId = createGroup(hostAToken, "NONE", 0);
        settleCycle(hostAToken, groupId);
        long contribution = contributionOf(hostAToken, groupId, 1);
        long memberId = memberProfileIdOf(groupId, contribution);

        JsonNode response = quickPay(hostAToken, groupId, """
                {"memberProfileId":%d,"amountMinor":500000,"currency":"VND","method":"BANK_TRANSFER"}
                """.formatted(memberId));
        assertThat(response.path("allocations")).hasSize(1);
        assertThat(response.path("allocations").path(0).path("amountMinor").asLong()).isEqualTo(500_000);
        assertThat(entryStatus(contribution)).isEqualTo("PARTIAL");
    }

    @Test
    void quickPayOverAmountIsRejectedBeforeAnyWrite() throws Exception {
        long groupId = createGroup(hostAToken, "FIXED", 20000);
        long cycleId = settleCycle(hostAToken, groupId);
        backdateContributions(cycleId, 3);
        assessFees(hostAToken, groupId);
        long contribution = contributionOf(hostAToken, groupId, 1);
        long memberId = memberProfileIdOf(groupId, contribution);

        int status = quickPayStatus(hostAToken, groupId, """
                {"memberProfileId":%d,"amountMinor":900000,"currency":"VND"}
                """.formatted(memberId));
        assertThat(status).isEqualTo(400);
        assertThat(entryStatus(contribution)).isEqualTo("UNPAID");
        assertThat(paymentCount(groupId)).isZero();
    }

    @Test
    void quickPayNoOutstandingObligationsIsRejected() throws Exception {
        long groupId = createGroup(hostAToken, "NONE", 0);
        settleCycle(hostAToken, groupId);

        // share 1 won: its member has a PAYOUT (direction OUT), never auto-paid
        long winnerMember = sharesOf(hostAToken, groupId).path(0).path("memberProfileId").asLong();
        int status = quickPayStatus(hostAToken, groupId, """
                {"memberProfileId":%d,"amountMinor":100000,"currency":"VND"}
                """.formatted(winnerMember));
        assertThat(status).isEqualTo(400);
        assertThat(paymentCount(groupId)).isZero();

        // once fully paid, a paying member also has nothing left
        long contribution = contributionOf(hostAToken, groupId, 1);
        long payerMember = memberProfileIdOf(groupId, contribution);
        quickPay(hostAToken, groupId, """
                {"memberProfileId":%d,"amountMinor":800000,"currency":"VND"}
                """.formatted(payerMember));
        int second = quickPayStatus(hostAToken, groupId, """
                {"memberProfileId":%d,"amountMinor":100000,"currency":"VND"}
                """.formatted(payerMember));
        assertThat(second).isEqualTo(400);
    }

    @Test
    void quickPayMemberNotInGroupIs404() throws Exception {
        long groupId = createGroup(hostAToken, "NONE", 0);
        settleCycle(hostAToken, groupId);
        long foreignMember = createMember(hostBToken);

        int status = quickPayStatus(hostAToken, groupId, """
                {"memberProfileId":%d,"amountMinor":800000,"currency":"VND"}
                """.formatted(foreignMember));
        assertThat(status).isEqualTo(404);
        assertThat(paymentCount(groupId)).isZero();
    }

    @Test
    void quickPayCrossOwnerAndAuthGuards() throws Exception {
        long groupId = createGroup(hostAToken, "NONE", 0);
        settleCycle(hostAToken, groupId);
        long contribution = contributionOf(hostAToken, groupId, 1);
        long memberId = memberProfileIdOf(groupId, contribution);

        assertThat(quickPayStatus(hostBToken, groupId, """
                {"memberProfileId":%d,"amountMinor":800000,"currency":"VND"}
                """.formatted(memberId))).isEqualTo(404);

        mockMvc.perform(post("/api/v1/groups/" + groupId + "/quick-pay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberProfileId\":" + memberId + ",\"amountMinor\":800000,\"currency\":\"VND\"}"))
                .andExpect(status().isUnauthorized());
        assertThat(paymentCount(groupId)).isZero();
    }

    @Test
    void quickPayIdempotencyReplaysOkAndRejectsDiffPayload() throws Exception {
        long groupId = createGroup(hostAToken, "NONE", 0);
        settleCycle(hostAToken, groupId);
        long contribution = contributionOf(hostAToken, groupId, 1);
        long memberId = memberProfileIdOf(groupId, contribution);
        String body = "{\"memberProfileId\":" + memberId + ",\"amountMinor\":800000,\"currency\":\"VND\"}";

        MvcResult first = mockMvc.perform(post("/api/v1/groups/" + groupId + "/quick-pay")
                        .header("Authorization", "Bearer " + hostAToken)
                        .header("Idempotency-Key", "qp-replay-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        long paymentId = objectMapper.readTree(first.getResponse().getContentAsString()).path("id").asLong();
        assertThat(paymentId).isPositive();

        MvcResult replay = mockMvc.perform(post("/api/v1/groups/" + groupId + "/quick-pay")
                        .header("Authorization", "Bearer " + hostAToken)
                        .header("Idempotency-Key", "qp-replay-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(objectMapper.readTree(replay.getResponse().getContentAsString()).path("id").asLong())
                .isEqualTo(paymentId);
        assertThat(entryStatus(contribution)).isEqualTo("PAID");
        assertThat(paymentCount(groupId)).isEqualTo(1);

        // same key, different payload -> 409, still one payment
        int conflict = quickPayStatusWithKey(hostAToken, groupId, "qp-replay-1",
                "{\"memberProfileId\":" + memberId + ",\"amountMinor\":700000,\"currency\":\"VND\"}");
        assertThat(conflict).isEqualTo(409);
        assertThat(paymentCount(groupId)).isEqualTo(1);
    }

    @Test
    void quickPayInputValidationRejected() throws Exception {
        long groupId = createGroup(hostAToken, "NONE", 0);
        settleCycle(hostAToken, groupId);
        long contribution = contributionOf(hostAToken, groupId, 1);
        long memberId = memberProfileIdOf(groupId, contribution);

        assertThat(quickPayStatus(hostAToken, groupId,
                "{\"memberProfileId\":" + memberId + ",\"amountMinor\":0,\"currency\":\"VND\"}")).isEqualTo(400);
        assertThat(quickPayStatus(hostAToken, groupId,
                "{\"amountMinor\":800000,\"currency\":\"VND\"}")).isEqualTo(400);
        assertThat(quickPayStatus(hostAToken, groupId,
                "{\"memberProfileId\":" + memberId + ",\"amountMinor\":800000,\"currency\":\"USD\"}")).isEqualTo(409);
        assertThat(quickPayStatus(hostAToken, groupId,
                "{\"memberProfileId\":" + memberId + ",\"amountMinor\":800000,\"currency\":\"VND\",\"method\":\"CARD\"}")).isEqualTo(400);
        assertThat(paymentCount(groupId)).isZero();
    }

    @Test
    void quickPayThenAttachReceiptOnTheSavedPayment() throws Exception {
        long groupId = createGroup(hostAToken, "NONE", 0);
        settleCycle(hostAToken, groupId);
        long contribution = contributionOf(hostAToken, groupId, 1);
        long memberId = memberProfileIdOf(groupId, contribution);

        JsonNode response = quickPay(hostAToken, groupId, """
                {"memberProfileId":%d,"amountMinor":800000,"currency":"VND","method":"BANK_TRANSFER"}
                """.formatted(memberId));
        long paymentId = response.path("id").asLong();
        assertThat(response.path("allocations")).hasSize(1);

        MvcResult upload = mockMvc.perform(multipart("/api/v1/payments/{paymentId}/attachments", paymentId)
                        .file(new MockMultipartFile("file", "receipt.png", "image/png",
                                "PNG-QP-RECEIPT".getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", "Bearer " + hostAToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode meta = objectMapper.readTree(upload.getResponse().getContentAsString());
        assertThat(meta.path("entityType").asText()).isEqualTo("PAYMENT");
        assertThat(meta.path("entityId").asLong()).isEqualTo(paymentId);

        MvcResult history = mockMvc.perform(get("/api/v1/groups/" + groupId + "/payments")
                        .header("Authorization", "Bearer " + hostAToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode rows = objectMapper.readTree(history.getResponse().getContentAsString());
        assertThat(rows).hasSize(1);
        assertThat(rows.path(0).path("attachments").path(0).path("originalName").asText()).isEqualTo("receipt.png");
    }

    private int quickPayStatusWithKey(String token, long groupId, String key, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/groups/" + groupId + "/quick-pay")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn().getResponse().getStatus();
    }

    private long createMember(String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/members")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Foreign Member\",\"phone\":\"%s\"}".formatted(randomPhone())))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("id").asLong();
    }
}
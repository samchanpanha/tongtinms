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

@SpringBootTest
@AutoConfigureMockMvc
class PaymentTests {

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
        return "10.16." + ThreadLocalRandom.current().nextInt(256) + "." + ThreadLocalRandom.current().nextInt(256);
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

    private long createGroup(String token, int shareCount) throws Exception {
        String groupBody = """
                {"name":"Hoi Payments","type":"BIDDING","baseAmount":1000000,"shareCount":%d,"cycleUnit":"MONTH",
                 "cycleCount":%d,"currency":"VND","maxBid":500000,"bidStep":10000,"bidCloseOffset":7}
                """.formatted(shareCount, shareCount);
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

    /**
     * Builds a settled cycle 1 of a 3-share group: contributions of 800000 for
     * share2 and share3 (UNPAID), payout of 1600000 for share1 (PAID after confirm).
     */
    private Fixture setupSettledCycle(String token) throws Exception {
        long groupId = createGroup(token, 3);
        JsonNode cycle = openCycle(token, groupId);
        long cycleId = cycle.path("id").asLong();
        JsonNode shares = sharesOf(token, groupId);
        mockMvc.perform(post("/api/v1/cycles/" + cycleId + "/bids")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shareId\":%d,\"amountMinor\":200000}".formatted(shares.path(0).path("id").asLong())))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/cycles/" + cycleId + "/close-and-calculate")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/cycles/" + cycleId + "/confirm-payout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        List<Map<String, Object>> contributions = jdbcTemplate.queryForList(
                "SELECT id, share_id FROM ledger_entries WHERE cycle_id = ? AND type = 'CONTRIBUTION' ORDER BY share_id",
                cycleId);
        assertThat(contributions).hasSize(2);
        long payoutId = jdbcTemplate.queryForObject(
                "SELECT id FROM ledger_entries WHERE cycle_id = ? AND type = 'PAYOUT'", Long.class, cycleId);
        return new Fixture(groupId, cycleId,
                ((Number) contributions.get(0).get("id")).longValue(),
                ((Number) contributions.get(1).get("id")).longValue(),
                payoutId);
    }

    private record Fixture(long groupId, long cycleId, long entryShare2, long entryShare3, long payoutEntry) {
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

    private MvcResult pay(String token, long groupId, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/groups/" + groupId + "/payments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
    }

    private MvcResult debts(String token, long groupId) throws Exception {
        return mockMvc.perform(get("/api/v1/groups/" + groupId + "/debts")
                        .header("Authorization", "Bearer " + token))
                .andReturn();
    }

    private String entryStatus(long entryId) {
        return jdbcTemplate.queryForObject("SELECT status FROM ledger_entries WHERE id = ?", String.class, entryId);
    }

    private long paymentCount(long groupId) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM payments WHERE group_id = ?", Long.class, groupId);
    }

    @Test
    void paymentAllocatesFullyAndMarksPaid() throws Exception {
        Fixture f = setupSettledCycle(hostAToken);
        String body = """
                {"amountMinor":800000,"currency":"VND","method":"BANK_TRANSFER",
                 "paidAt":"2026-10-06T10:00:00Z","note":"tien mat",
                 "allocations":[{"ledgerEntryId":%d,"amountMinor":800000}]}
                """.formatted(f.entryShare2());

        MvcResult result = pay(hostAToken, f.groupId(), body);
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(response.path("amountMinor").asLong()).isEqualTo(800_000);
        assertThat(response.path("currency").asText()).isEqualTo("VND");
        assertThat(response.path("method").asText()).isEqualTo("BANK_TRANSFER");
        assertThat(response.path("paidAt").asText()).isEqualTo("2026-10-06T10:00:00Z");
        assertThat(response.path("createdAt").asText()).isNotBlank();
        assertThat(response.path("note").asText()).isEqualTo("tien mat");
        assertThat(response.path("allocations").path(0).path("ledgerEntryId").asLong()).isEqualTo(f.entryShare2());
        assertThat(response.path("allocations").path(0).path("amountMinor").asLong()).isEqualTo(800_000);

        assertThat(entryStatus(f.entryShare2())).isEqualTo("PAID");
        assertThat(paymentCount(f.groupId())).isEqualTo(1);
        Long hostId = jdbcTemplate.queryForObject(
                "SELECT received_by_host_id FROM payments WHERE group_id = ?", Long.class, f.groupId());
        assertThat(hostId).isNotNull();
        Long allocationCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM payment_allocations WHERE ledger_entry_id = ?", Long.class, f.entryShare2());
        assertThat(allocationCount).isEqualTo(1);
    }

    @Test
    void partialPaymentMarksPartialThenCompletes() throws Exception {
        Fixture f = setupSettledCycle(hostAToken);

        MvcResult first = pay(hostAToken, f.groupId(), """
                {"amountMinor":400000,"currency":"VND","method":"CASH",
                 "allocations":[{"ledgerEntryId":%d,"amountMinor":400000}]}
                """.formatted(f.entryShare3()));
        assertThat(first.getResponse().getStatus()).isEqualTo(201);
        assertThat(entryStatus(f.entryShare3())).isEqualTo("PARTIAL");

        MvcResult second = pay(hostAToken, f.groupId(), """
                {"amountMinor":400000,"currency":"VND","method":"CASH",
                 "allocations":[{"ledgerEntryId":%d,"amountMinor":400000}]}
                """.formatted(f.entryShare3()));
        assertThat(second.getResponse().getStatus()).isEqualTo(201);
        assertThat(entryStatus(f.entryShare3())).isEqualTo("PAID");
        assertThat(paymentCount(f.groupId())).isEqualTo(2);

        long allocated = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(amount_minor),0) FROM payment_allocations WHERE ledger_entry_id = ?",
                Long.class, f.entryShare3());
        assertThat(allocated).isEqualTo(800_000);
    }

    @Test
    void overAllocationRejected() throws Exception {
        Fixture f = setupSettledCycle(hostAToken);
        MvcResult first = pay(hostAToken, f.groupId(), """
                {"amountMinor":600000,"currency":"VND",
                 "allocations":[{"ledgerEntryId":%d,"amountMinor":600000}]}
                """.formatted(f.entryShare2()));
        assertThat(first.getResponse().getStatus()).isEqualTo(201);
        assertThat(entryStatus(f.entryShare2())).isEqualTo("PARTIAL");

        MvcResult second = pay(hostAToken, f.groupId(), """
                {"amountMinor":300000,"currency":"VND",
                 "allocations":[{"ledgerEntryId":%d,"amountMinor":300000}]}
                """.formatted(f.entryShare2()));
        assertThat(second.getResponse().getStatus()).isEqualTo(400);
        assertThat(paymentCount(f.groupId())).isEqualTo(1);
        assertThat(entryStatus(f.entryShare2())).isEqualTo("PARTIAL");

        long allocated = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(amount_minor),0) FROM payment_allocations WHERE ledger_entry_id = ?",
                Long.class, f.entryShare2());
        assertThat(allocated).isEqualTo(600_000);
    }

    @Test
    void allocationSumMustEqualPaymentAmount() throws Exception {
        Fixture f = setupSettledCycle(hostAToken);

        MvcResult under = pay(hostAToken, f.groupId(), """
                {"amountMinor":500000,"currency":"VND",
                 "allocations":[{"ledgerEntryId":%d,"amountMinor":800000}]}
                """.formatted(f.entryShare2()));
        assertThat(under.getResponse().getStatus()).isEqualTo(400);

        MvcResult over = pay(hostAToken, f.groupId(), """
                {"amountMinor":1000000,"currency":"VND",
                 "allocations":[{"ledgerEntryId":%d,"amountMinor":800000}]}
                """.formatted(f.entryShare2()));
        assertThat(over.getResponse().getStatus()).isEqualTo(400);

        assertThat(paymentCount(f.groupId())).isZero();
        assertThat(entryStatus(f.entryShare2())).isEqualTo("UNPAID");
    }

    @Test
    void currencyMismatchIs409() throws Exception {
        Fixture f = setupSettledCycle(hostAToken);
        MvcResult result = pay(hostAToken, f.groupId(), """
                {"amountMinor":800000,"currency":"USD",
                 "allocations":[{"ledgerEntryId":%d,"amountMinor":800000}]}
                """.formatted(f.entryShare2()));
        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(paymentCount(f.groupId())).isZero();
    }

    @Test
    void foreignLedgerEntryIs404() throws Exception {
        Fixture a = setupSettledCycle(hostAToken);
        Fixture b = setupSettledCycle(hostBToken);

        MvcResult result = pay(hostAToken, a.groupId(), """
                {"amountMinor":800000,"currency":"VND",
                 "allocations":[{"ledgerEntryId":%d,"amountMinor":800000}]}
                """.formatted(b.entryShare2()));
        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(paymentCount(a.groupId())).isZero();
        assertThat(entryStatus(b.entryShare2())).isEqualTo("UNPAID");
    }

    @Test
    void cannotAllocateToSettledObligation() throws Exception {
        Fixture f = setupSettledCycle(hostAToken);
        MvcResult result = pay(hostAToken, f.groupId(), """
                {"amountMinor":1600000,"currency":"VND",
                 "allocations":[{"ledgerEntryId":%d,"amountMinor":1600000}]}
                """.formatted(f.payoutEntry()));
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(paymentCount(f.groupId())).isZero();
    }

    @Test
    void emptyAllocationsRejected() throws Exception {
        Fixture f = setupSettledCycle(hostAToken);
        MvcResult result = pay(hostAToken, f.groupId(),
                "{\"amountMinor\":800000,\"currency\":\"VND\",\"allocations\":[]}");
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(paymentCount(f.groupId())).isZero();
    }

    @Test
    void duplicateEntryInAllocationsRejected() throws Exception {
        Fixture f = setupSettledCycle(hostAToken);
        MvcResult result = pay(hostAToken, f.groupId(), """
                {"amountMinor":800000,"currency":"VND",
                 "allocations":[
                   {"ledgerEntryId":%d,"amountMinor":400000},
                   {"ledgerEntryId":%d,"amountMinor":400000}]}
                """.formatted(f.entryShare2(), f.entryShare2()));
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(paymentCount(f.groupId())).isZero();
    }

    @Test
    void invalidPaymentInputRejected() throws Exception {
        Fixture f = setupSettledCycle(hostAToken);

        MvcResult zero = pay(hostAToken, f.groupId(), """
                {"amountMinor":0,"currency":"VND",
                 "allocations":[{"ledgerEntryId":%d,"amountMinor":800000}]}
                """.formatted(f.entryShare2()));
        assertThat(zero.getResponse().getStatus()).isEqualTo(400);

        MvcResult badMethod = pay(hostAToken, f.groupId(), """
                {"amountMinor":800000,"currency":"VND","method":"GOLD",
                 "allocations":[{"ledgerEntryId":%d,"amountMinor":800000}]}
                """.formatted(f.entryShare2()));
        assertThat(badMethod.getResponse().getStatus()).isEqualTo(400);

        MvcResult badDate = pay(hostAToken, f.groupId(), """
                {"amountMinor":800000,"currency":"VND","paidAt":"yesterday",
                 "allocations":[{"ledgerEntryId":%d,"amountMinor":800000}]}
                """.formatted(f.entryShare2()));
        assertThat(badDate.getResponse().getStatus()).isEqualTo(400);

        assertThat(paymentCount(f.groupId())).isZero();
    }

    @Test
    void debtsListOnlyOverdueWithRemaining() throws Exception {
        Fixture f = setupSettledCycle(hostAToken);
        jdbcTemplate.update("UPDATE ledger_entries SET due_at = now() - interval '3 days' WHERE id = ?",
                f.entryShare2());
        assertThat(entryStatus(f.entryShare3())).isEqualTo("UNPAID");

        MvcResult before = debts(hostAToken, f.groupId());
        assertThat(before.getResponse().getStatus()).isEqualTo(200);
        JsonNode list = objectMapper.readTree(before.getResponse().getContentAsString());
        assertThat(list).hasSize(1);
        JsonNode debt = list.path(0);
        assertThat(debt.path("ledgerEntryId").asLong()).isEqualTo(f.entryShare2());
        assertThat(debt.path("cycleNo").asInt()).isEqualTo(1);
        assertThat(debt.path("type").asText()).isEqualTo("CONTRIBUTION");
        assertThat(debt.path("amountMinor").asLong()).isEqualTo(800_000);
        assertThat(debt.path("allocatedMinor").asLong()).isZero();
        assertThat(debt.path("remainingMinor").asLong()).isEqualTo(800_000);
        assertThat(debt.path("currency").asText()).isEqualTo("VND");
        assertThat(debt.path("status").asText()).isEqualTo("UNPAID");
        assertThat(debt.path("overdueDays").asLong()).isEqualTo(3);

        pay(hostAToken, f.groupId(), """
                {"amountMinor":500000,"currency":"VND",
                 "allocations":[{"ledgerEntryId":%d,"amountMinor":500000}]}
                """.formatted(f.entryShare2()));

        JsonNode afterPartial = objectMapper.readTree(debts(hostAToken, f.groupId())
                .getResponse().getContentAsString());
        assertThat(afterPartial).hasSize(1);
        assertThat(afterPartial.path(0).path("status").asText()).isEqualTo("PARTIAL");
        assertThat(afterPartial.path(0).path("allocatedMinor").asLong()).isEqualTo(500_000);
        assertThat(afterPartial.path(0).path("remainingMinor").asLong()).isEqualTo(300_000);

        pay(hostAToken, f.groupId(), """
                {"amountMinor":300000,"currency":"VND",
                 "allocations":[{"ledgerEntryId":%d,"amountMinor":300000}]}
                """.formatted(f.entryShare2()));

        JsonNode afterPaid = objectMapper.readTree(debts(hostAToken, f.groupId())
                .getResponse().getContentAsString());
        assertThat(afterPaid).isEmpty();
    }

    @Test
    void crossOwnerAndAuthGuards() throws Exception {
        Fixture a = setupSettledCycle(hostAToken);

        String paymentBody = """
                {"amountMinor":800000,"currency":"VND",
                 "allocations":[{"ledgerEntryId":%d,"amountMinor":800000}]}
                """.formatted(a.entryShare2());
        assertThat(pay(hostBToken, a.groupId(), paymentBody).getResponse().getStatus()).isEqualTo(404);
        assertThat(debts(hostBToken, a.groupId()).getResponse().getStatus()).isEqualTo(404);

        mockMvc.perform(post("/api/v1/groups/" + a.groupId() + "/payments"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/groups/" + a.groupId() + "/debts"))
                .andExpect(status().isUnauthorized());

        assertThat(paymentCount(a.groupId())).isZero();
    }
}

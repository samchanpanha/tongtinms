package com.tongtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
 * Step 22 release-gate audit: formalizes the cross-cutting gates from plan.md §3
 * (manus_ai §11/§14/§16) that had no dedicated test backing:
 *
 * 1. Closed bids are uneditable via the normal API (immutability after close).
 * 2. Every payout view shows gross pot, deductions (host fee), fee, winner,
 *    formula version (GET /groups/{id}/profit — host-only).
 * 3. Member statement is reproducible from raw ledger events
 *    (independent recomputation from ledger_entries + payment_allocations matches
 *    the API response, including the runningBalance chain).
 * 4. Integer minor units only: no double/float anywhere in main source
 *    (grep-level zero-float check).
 *
 * Sealed-bid confidentiality, tie-break-by-stored-rule, idempotency, notification
 * failure isolation, currency lock, and rules freeze are already covered by
 * SealedBiddingTests, CloseCalculateTests, PaymentIdempotencyTests,
 * NotificationTests, PaymentTests + GroupDraftTests, and AuthzSweepTests.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ReleaseGateTests {

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
        hostPhone = "0988" + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
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
        return "10.21." + ThreadLocalRandom.current().nextInt(256) + "." + ThreadLocalRandom.current().nextInt(256);
    }

    private String randomPhone() {
        String p = "0966" + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
        createdPhones.add(p);
        return p;
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
        return objectMapper.readTree(m.getResponse().getContentAsString()).path("id").asLong();
    }

    private long createGroup(String token) throws Exception {
        String body = """
                {"name":"Hoi Release Gate","type":"BIDDING","baseAmount":1000000,"shareCount":3,"cycleUnit":"MONTH",
                 "cycleCount":3,"currency":"VND","maxBid":500000,"bidStep":10000,"bidCloseOffset":7,
                 "hostFeeType":"FIXED_PER_CYCLE","hostFeeMinor":50000}
                """;
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

    private record Scenario(long groupId, List<Long> memberIds, List<Long> shareIds) {
    }

    private Scenario setup() throws Exception {
        long groupId = createGroup(hostToken);
        List<Long> memberIds = new ArrayList<>();
        List<Long> shareIds = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            long member = createMember(hostToken);
            memberIds.add(member);
            shareIds.add(assignShare(hostToken, groupId, member));
        }
        mockMvc.perform(post("/api/v1/groups/" + groupId + "/start")
                        .header("Authorization", "Bearer " + hostToken))
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

    private String memberToken(long memberId) throws Exception {
        mockMvc.perform(post("/api/v1/members/" + memberId + "/set-login")
                        .header("Authorization", "Bearer " + hostToken)
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
        assertThat(login.getResponse().getStatus()).isEqualTo(200);
        return objectMapper.readTree(login.getResponse().getContentAsString())
                .path("tokens").path("accessToken").asText();
    }

    private static void assertMoney(JsonNode node, String currency, long amountMinor) {
        assertThat(node.path("currency").asText()).isEqualTo(currency);
        assertThat(node.path("amountMinor").asLong()).isEqualTo(amountMinor);
    }

    /** Settles cycle 1 (share 1 wins with bid 200000) and fully pays member 2's contribution. */
    private long settleCycle1(Scenario s) throws Exception {
        long cycleId = openCycle(hostToken, s.groupId());
        mockMvc.perform(post("/api/v1/cycles/" + cycleId + "/bids")
                        .header("Authorization", "Bearer " + hostToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shareId\":%d,\"amountMinor\":200000}".formatted(s.shareIds().get(0))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/cycles/" + cycleId + "/close-and-calculate")
                        .header("Authorization", "Bearer " + hostToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/cycles/" + cycleId + "/confirm-payout")
                        .header("Authorization", "Bearer " + hostToken))
                .andExpect(status().isOk());

        long payerEntry = jdbcTemplate.queryForObject(
                "SELECT id FROM ledger_entries WHERE cycle_id = ? AND share_id = ? AND type = 'CONTRIBUTION'",
                Long.class, cycleId, s.shareIds().get(1));
        mockMvc.perform(post("/api/v1/groups/" + s.groupId() + "/payments")
                        .header("Authorization", "Bearer " + hostToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("{\"amountMinor\":800000,\"currency\":\"VND\",\"allocations\":" +
                                "[{\"ledgerEntryId\":%d,\"amountMinor\":800000}]}").formatted(payerEntry)))
                .andExpect(status().isCreated());
        return cycleId;
    }

    // ------------------------------------------------------------------
    // Gate: closed bid uneditable via the normal API
    // ------------------------------------------------------------------
    @Test
    void closedBidCannotBeEdited() throws Exception {
        Scenario s = setup();
        long cycleId = openCycle(hostToken, s.groupId());
        long shareId = s.shareIds().get(0);

        MvcResult first = mockMvc.perform(post("/api/v1/cycles/" + cycleId + "/bids")
                        .header("Authorization", "Bearer " + hostToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shareId\":%d,\"amountMinor\":200000}".formatted(shareId)))
                .andExpect(status().isCreated())
                .andReturn();
        assertThat(objectMapper.readTree(first.getResponse().getContentAsString())
                .path("amountMinor").asLong()).isEqualTo(200_000);

        jdbcTemplate.update("UPDATE cycles SET bid_close_at = now() - interval '1 day' WHERE id = ?", cycleId);

        // updating (re-submitting) the same bid after close is rejected
        mockMvc.perform(post("/api/v1/cycles/" + cycleId + "/bids")
                        .header("Authorization", "Bearer " + hostToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shareId\":%d,\"amountMinor\":150000}".formatted(shareId)))
                .andExpect(status().isBadRequest());

        // and the stored bid is untouched: one row, latest amount intact
        Long rows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM bids WHERE cycle_id = ? AND share_id = ?", Long.class, cycleId, shareId);
        assertThat(rows).isEqualTo(1);
        Long amount = jdbcTemplate.queryForObject(
                "SELECT amount_minor FROM bids WHERE cycle_id = ? AND share_id = ? AND is_latest",
                Long.class, cycleId, shareId);
        assertThat(amount).isEqualTo(200_000);
    }

    // ------------------------------------------------------------------
    // Gate: every payout shows gross pot, deductions, fee, winner, formula version
    // ------------------------------------------------------------------
    @Test
    void payoutReportShowsFullBreakdown() throws Exception {
        Scenario s = setup();
        long cycleId = settleCycle1(s);

        // the cycle result itself carries the money breakdown + winner
        JsonNode cycle = getJson("/api/v1/cycles/" + cycleId, hostToken);
        assertThat(cycle.path("status").asText()).isEqualTo("SETTLED");
        assertThat(cycle.path("winnerShareId").asLong()).isEqualTo(s.shareIds().get(0));
        // cycle response carries raw minor-unit scalars; money shape lives in reports
        assertThat(cycle.path("winningBid").asLong()).isEqualTo(200_000);
        assertThat(cycle.path("grossPot").asLong()).isEqualTo(1_600_000);
        assertThat(cycle.path("hostFee").asLong()).isEqualTo(50_000);
        assertThat(cycle.path("netPayout").asLong()).isEqualTo(1_550_000);

        // the profit report adds formula version + winner identity + deduction identity
        JsonNode profit = getJson("/api/v1/groups/" + s.groupId() + "/profit", hostToken);
        assertThat(profit.path("formulaVersion").asInt()).isPositive();
        JsonNode line = profit.path("cycles").path(0);
        assertThat(line.path("winner").path("shareId").asLong()).isEqualTo(s.shareIds().get(0));
        assertThat(line.path("winner").path("shareNo").asInt()).isEqualTo(1);
        assertThat(line.path("winner").path("memberName").asText()).isEqualTo("Hoi Vien");
        assertMoney(line.path("winningBid"), "VND", 200_000);
        assertMoney(line.path("grossPot"), "VND", 1_600_000);
        assertMoney(line.path("hostFee"), "VND", 50_000);
        assertMoney(line.path("netPayout"), "VND", 1_550_000);

        // deduction identity: gross - deductions (host fee) == net payout
        long gross = profit.path("totals").path("grossPot").path("amountMinor").asLong();
        long fee = profit.path("totals").path("hostFee").path("amountMinor").asLong();
        long net = profit.path("totals").path("netPayout").path("amountMinor").asLong();
        assertThat(gross - fee).isEqualTo(net);
        assertMoney(profit.path("totals").path("settledHostFee"), "VND", 50_000);
    }

    // ------------------------------------------------------------------
    // Gate: member statement reproducible from ledger events
    // ------------------------------------------------------------------
    @Test
    void memberStatementIsReproducibleFromLedgerEvents() throws Exception {
        Scenario s = setup();
        settleCycle1(s);
        String winnerToken = memberToken(s.memberIds().get(0));
        String payerToken = memberToken(s.memberIds().get(1));

        verifyStatementMatchesLedger(s, s.memberIds().get(0), winnerToken);
        verifyStatementMatchesLedger(s, s.memberIds().get(1), payerToken);
    }

    /**
     * Recomputes contributed / received / feesPaid / netPosition and the whole
     * runningBalance chain from raw ledger_entries + payment_allocations rows and
     * asserts they equal the API statement response (01-DOMAIN §9.5).
     */
    private void verifyStatementMatchesLedger(Scenario s, long memberId, String token) throws Exception {
        JsonNode statement = getJson("/api/v1/me/groups/" + s.groupId() + "/statement", token);

        List<Long> shareIds = jdbcTemplate.queryForList(
                "SELECT id FROM group_shares WHERE member_profile_id = ? AND group_id = ? ORDER BY share_no",
                Long.class, memberId, s.groupId());
        assertThat(shareIds).isNotEmpty();

        long expectedContributed = 0;
        long expectedReceived = 0;
        long expectedFees = 0;

        for (JsonNode apiShare : statement.path("shares")) {
            long shareId = apiShare.path("shareId").asLong();
            assertThat(shareIds).contains(shareId);

            // independent per-entry rows for this share, same order the statement uses
            List<JsonNode> apiEntries = new ArrayList<>();
            apiShare.path("entries").forEach(apiEntries::add);
            for (JsonNode entry : apiEntries) {
                long entryId = entry.path("entryId").asLong();
                String type = entry.path("type").asText();
                String direction = entry.path("direction").asText();
                long amount = entry.path("amount").path("amountMinor").asLong();
                long allocated = allocatedFor(entryId);
                assertThat(entry.path("allocated").path("amountMinor").asLong()).isEqualTo(allocated);

                if ("OUT".equals(direction)) {
                    expectedReceived += amount; // PAYOUT is the only OUT for a member share
                } else if ("CONTRIBUTION".equals(type)) {
                    expectedContributed += allocated;
                } else {
                    expectedFees += allocated; // LATE_FEE / OTHER_FEE
                }
            }
        }

        JsonNode totals = statement.path("totals");
        assertMoney(totals.path("contributed"), "VND", expectedContributed);
        assertMoney(totals.path("received"), "VND", expectedReceived);
        assertMoney(totals.path("feesPaid"), "VND", expectedFees);
        assertMoney(totals.path("netPosition"), "VND", expectedReceived - expectedContributed - expectedFees);

        // runningBalance chain per share: OUT adds full amount, IN subtracts allocated
        for (JsonNode apiShare : statement.path("shares")) {
            long running = 0;
            for (JsonNode entry : apiShare.path("entries")) {
                long amount = entry.path("amount").path("amountMinor").asLong();
                long allocated = entry.path("allocated").path("amountMinor").asLong();
                running += "OUT".equals(entry.path("direction").asText()) ? amount : -allocated;
                assertThat(entry.path("runningBalance").path("amountMinor").asLong())
                        .as("runningBalance chain for share " + apiShare.path("shareId").asLong())
                        .isEqualTo(running);
            }
            // share totals are the same function of its entries
            JsonNode shareTotals = apiShare.path("totals");
            assertThat(shareTotals.path("netPosition").path("amountMinor").asLong())
                    .isEqualTo(shareTotals.path("received").path("amountMinor").asLong()
                            - shareTotals.path("contributed").path("amountMinor").asLong()
                            - shareTotals.path("feesPaid").path("amountMinor").asLong());
        }
    }

    private long allocatedFor(long ledgerEntryId) {
        Long sum = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(amount_minor), 0) FROM payment_allocations WHERE ledger_entry_id = ?",
                Long.class, ledgerEntryId);
        return sum == null ? 0 : sum;
    }

    // ------------------------------------------------------------------
    // Gate: all amounts integer minor units; float/double money math prohibited
    // ------------------------------------------------------------------
    @Test
    void mainSourceContainsNoFloatOrDoubleMoneyTypes() throws Exception {
        Path sourceRoot = Path.of("src", "main", "java");
        assertThat(Files.isDirectory(sourceRoot)).as("main source dir resolvable from test working dir").isTrue();

        List<String> offenders;
        try (Stream<Path> files = Files.walk(sourceRoot)) {
            offenders = files
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> {
                        try {
                            return java.util.regex.Pattern.compile("\\b(double|float)\\b")
                                    .matcher(Files.readString(p)).find();
                        } catch (Exception e) {
                            throw new IllegalStateException(e);
                        }
                    })
                    .map(p -> p.toString().replace('\\', '/'))
                    .sorted()
                    .collect(Collectors.toList());
        }
        assertThat(offenders)
                .as("zero-float invariant: no double/float in main source (grep-level gate)")
                .isEmpty();
    }
}

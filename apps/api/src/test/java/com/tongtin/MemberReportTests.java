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
 * Step 15 member reports: GET /me/groups/{id}/statement and GET /me/groups/{id}/cycles
 * (public cycle summaries). Host fee and host profit must never appear.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MemberReportTests {

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

    private long createGroup(String token) throws Exception {
        String body = """
                {"name":"Hoi Member Report","type":"BIDDING","baseAmount":1000000,"shareCount":3,"cycleUnit":"MONTH",
                 "cycleCount":3,"currency":"VND","maxBid":500000,"bidStep":10000,"bidCloseOffset":7,
                 "hostFeeType":"FIXED_PER_CYCLE","hostFeeMinor":50000}
                """.formatted();
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

    /** 3 members each with one share. */
    private Scenario setup() throws Exception {
        long groupId = createGroup(hostAToken);
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

    /** Provisions a login and returns the member access token. */
    private String memberToken(long memberId) throws Exception {
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
        assertThat(login.getResponse().getStatus()).isEqualTo(200);
        return objectMapper.readTree(login.getResponse().getContentAsString())
                .path("tokens").path("accessToken").asText();
    }

    /** Settles cycle 1 (winner = share 1, bid 200000) and pays member 2's contribution. */
    private long settleCycle1(Scenario s) throws Exception {
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
        long contribution = 800_000;
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
    void statementShowsOwnShareLifecycle() throws Exception {
        Scenario s = setup();
        settleCycle1(s);
        String winnerToken = memberToken(s.memberIds().get(0));
        String payerToken = memberToken(s.memberIds().get(1));

        JsonNode winner = getJson("/api/v1/me/groups/" + s.groupId() + "/statement", winnerToken);
        assertThat(winner.path("currency").asText()).isEqualTo("VND");
        assertThat(winner.path("shares")).hasSize(1);
        JsonNode share = winner.path("shares").path(0);
        assertThat(share.path("shareNo").asInt()).isEqualTo(1);
        assertThat(share.path("entries")).hasSize(1);
        JsonNode payout = share.path("entries").path(0);
        assertThat(payout.path("type").asText()).isEqualTo("PAYOUT");
        assertThat(payout.path("direction").asText()).isEqualTo("OUT");
        assertThat(payout.path("status").asText()).isEqualTo("PAID");
        assertThat(payout.path("cycleNo").asInt()).isEqualTo(1);
        assertMoney(payout.path("amount"), "VND", 1_550_000);
        assertMoney(payout.path("runningBalance"), "VND", 1_550_000);
        assertMoney(share.path("totals").path("contributed"), "VND", 0);
        assertMoney(share.path("totals").path("received"), "VND", 1_550_000);
        assertMoney(share.path("totals").path("feesPaid"), "VND", 0);
        assertMoney(share.path("totals").path("netPosition"), "VND", 1_550_000);

        JsonNode payer = getJson("/api/v1/me/groups/" + s.groupId() + "/statement", payerToken);
        JsonNode payerEntry = payer.path("shares").path(0).path("entries").path(0);
        assertThat(payerEntry.path("type").asText()).isEqualTo("CONTRIBUTION");
        assertThat(payerEntry.path("direction").asText()).isEqualTo("IN");
        assertThat(payerEntry.path("status").asText()).isEqualTo("PAID");
        assertMoney(payerEntry.path("amount"), "VND", 800_000);
        assertMoney(payerEntry.path("runningBalance"), "VND", -800_000);
        assertMoney(payer.path("shares").path(0).path("totals").path("contributed"), "VND", 800_000);
        assertMoney(payer.path("shares").path(0).path("totals").path("netPosition"), "VND", -800_000);
    }

    @Test
    void publicCyclesNeverLeakHostFeeOrProfit() throws Exception {
        Scenario s = setup();
        settleCycle1(s);
        String winnerToken = memberToken(s.memberIds().get(0));

        JsonNode cycles = getJson("/api/v1/me/groups/" + s.groupId() + "/cycles", winnerToken);
        assertThat(cycles.isArray()).isTrue();
        assertThat(cycles).hasSize(1);

        JsonNode c = cycles.path(0);
        assertThat(c.path("cycleNo").asInt()).isEqualTo(1);
        assertThat(c.path("status").asText()).isEqualTo("SETTLED");
        assertThat(c.path("winner").path("shareNo").asInt()).isEqualTo(1);
        assertThat(c.path("winner").path("memberName").asText()).isEqualTo("Hoi Vien");
        assertMoney(c.path("winningBid"), "VND", 200_000);
        assertMoney(c.path("grossPot"), "VND", 1_600_000);
        assertMoney(c.path("netPayout"), "VND", 1_550_000);

        // host-only figures must be absent from the public summary
        assertThat(c.path("hostFee").isMissingNode()).isTrue();
        assertThat(c.path("hostFeeMinor").isMissingNode()).isTrue();
        assertThat(c.path("hostProfit").isMissingNode()).isTrue();
        assertThat(c.path("totals").isMissingNode()).isTrue();
    }

    @Test
    void statementAndCyclesAreTenantScoped() throws Exception {
        Scenario s = setup();
        String memberToken = memberToken(s.memberIds().get(0));

        mockMvc.perform(get("/api/v1/me/groups/999999/statement")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/me/groups/999999/cycles")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/me/groups/" + s.groupId() + "/statement"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/me/groups/" + s.groupId() + "/cycles")
                        .header("Authorization", "Bearer " + hostAToken))
                .andExpect(status().isForbidden());

        // a member of another host's group is not found here either
        long otherGroup = createGroup(hostBToken);
        mockMvc.perform(get("/api/v1/me/groups/999999/statement")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/me/groups/" + otherGroup + "/statement")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isNotFound());
    }
}
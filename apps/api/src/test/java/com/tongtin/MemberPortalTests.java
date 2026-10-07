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

@SpringBootTest
@AutoConfigureMockMvc
class MemberPortalTests {

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
        return "10.18." + ThreadLocalRandom.current().nextInt(256) + "." + ThreadLocalRandom.current().nextInt(256);
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

    private JsonNode createMember(String token) throws Exception {
        MvcResult m = mockMvc.perform(post("/api/v1/members")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Hoi Vien\",\"phone\":\"%s\"}".formatted(randomPhone())))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode member = objectMapper.readTree(m.getResponse().getContentAsString());
        createdPhones.add(member.path("phone").asText());
        return member;
    }

    private long createGroup(String token) throws Exception {
        String groupBody = """
                {"name":"Hoi Member","type":"BIDDING","baseAmount":1000000,"shareCount":3,"cycleUnit":"MONTH",
                 "cycleCount":3,"currency":"VND","maxBid":500000,"bidStep":10000,"bidCloseOffset":7}
                """;
        MvcResult g = mockMvc.perform(post("/api/v1/groups")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(groupBody))
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
        return objectMapper.readTree(s.getResponse().getContentAsString())
                .path("shares").path(0).path("id").asLong();
    }

    /** Creates group, 3 members, shares, starts it. Returns [hostToken, groupId, memberIds] with member i owning share i. */
    private MemberHost setupHostGroup() throws Exception {
        long groupId = createGroup(hostAToken);
        List<Long> memberIds = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            JsonNode member = createMember(hostAToken);
            memberIds.add(member.path("id").asLong());
            assignShare(hostAToken, groupId, member.path("id").asLong());
        }
        mockMvc.perform(post("/api/v1/groups/" + groupId + "/start").header("Authorization", "Bearer " + hostAToken))
                .andExpect(status().isOk());
        return new MemberHost(groupId, memberIds);
    }

    private record MemberHost(long groupId, List<Long> memberIds) {
    }

    private String memberLoginToken(long memberId) throws Exception {
        String phone = jdbcTemplate.queryForObject(
                "SELECT phone FROM member_profiles WHERE id = ?", String.class, memberId);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", uniqueIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"%s\",\"password\":\"password123\"}".formatted(phone)))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("tokens").path("accessToken").asText();
    }

    private JsonNode myGroups(String token) throws Exception {
        MvcResult r = mockMvc.perform(get("/api/v1/me/groups").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString());
    }

    private long openCycle(String token, long groupId) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/v1/groups/" + groupId + "/cycles/open")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).path("id").asLong();
    }

    private long contributionEntry(long cycleId, long shareId) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM ledger_entries WHERE cycle_id = ? AND share_id = ? AND type = 'CONTRIBUTION'",
                Long.class, cycleId, shareId);
    }

    private long payoutEntry(long cycleId, long shareId) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM ledger_entries WHERE cycle_id = ? AND share_id = ? AND type = 'PAYOUT'",
                Long.class, cycleId, shareId);
    }

    @Test
    void hostSetsMemberLoginAndMemberLogsInWithMemberRole() throws Exception {
        MemberHost host = setupHostGroup();
        long memberId = host.memberIds().get(0);
        String memberPhone = jdbcTemplate.queryForObject(
                "SELECT phone FROM member_profiles WHERE id = ?", String.class, memberId);

        MvcResult set = mockMvc.perform(post("/api/v1/members/" + memberId + "/set-login")
                        .header("Authorization", "Bearer " + hostAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"password123\"}"))
                .andExpect(status().isOk()).andReturn();
        JsonNode setBody = objectMapper.readTree(set.getResponse().getContentAsString());
        assertThat(setBody.path("loginEnabled").asBoolean()).isTrue();
        assertThat(setBody.path("reset").asBoolean()).isFalse();
        assertThat(setBody.path("phone").asText()).isEqualTo(memberPhone);

        Long usersRow = jdbcTemplate.queryForObject(
                "SELECT u.id FROM users u JOIN user_roles r ON r.user_id = u.id WHERE u.phone = ? AND r.role = 'MEMBER'",
                Long.class, memberPhone);
        assertThat(usersRow).isNotNull();

        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", uniqueIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"%s\",\"password\":\"password123\"}".formatted(memberPhone)))
                .andExpect(status().isOk()).andReturn();
        JsonNode loginBody = objectMapper.readTree(login.getResponse().getContentAsString());
        assertThat(loginBody.path("roles")).hasSize(1);
        assertThat(loginBody.path("roles").path(0).asText()).isEqualTo("MEMBER");
        assertThat(loginBody.path("owner").isNull()).isTrue();
        assertThat(loginBody.path("tokens").path("accessToken").asText()).isNotBlank();

        MvcResult me = mockMvc.perform(get("/api/v1/me")
                        .header("Authorization", "Bearer " + memberLoginToken(memberId)))
                .andExpect(status().isOk()).andReturn();
        JsonNode meBody = objectMapper.readTree(me.getResponse().getContentAsString());
        assertThat(meBody.path("roles").path(0).asText()).isEqualTo("MEMBER");
        assertThat(meBody.path("owner").isMissingNode()).isTrue();
        assertThat(meBody.path("onboarding").isMissingNode()).isTrue();

        String wrongPassword = "wrongpass";
        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", uniqueIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"%s\",\"password\":\"%s\"}".formatted(memberPhone, wrongPassword)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void hostCannotTurnHostAccountPhoneIntoMemberLogin() throws Exception {
        MemberHost host = setupHostGroup();
        long memberId = host.memberIds().get(0);
        String memberPhone = jdbcTemplate.queryForObject(
                "SELECT phone FROM member_profiles WHERE id = ?", String.class, memberId);

        mockMvc.perform(post("/api/v1/members/" + memberId + "/set-login")
                        .header("Authorization", "Bearer " + hostAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"password123\"}"))
                .andExpect(status().isOk());

        // same member phone already logged in -> reset keeps working
        MvcResult reset = mockMvc.perform(post("/api/v1/members/" + memberId + "/set-login")
                        .header("Authorization", "Bearer " + hostAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"newpassword\"}"))
                .andExpect(status().isOk()).andReturn();
        assertThat(objectMapper.readTree(reset.getResponse().getContentAsString()).path("reset").asBoolean()).isTrue();
        assertThat(memberPhone).isNotEmpty();

        // paper profile whose phone equals the HOST's own phone cannot get a login
        MvcResult dupRes = mockMvc.perform(post("/api/v1/members")
                        .header("Authorization", "Bearer " + hostAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Clone Host\",\"phone\":\"%s\"}".formatted(hostAPhone)))
                .andExpect(status().isCreated()).andReturn();
        long dupId = objectMapper.readTree(dupRes.getResponse().getContentAsString()).path("id").asLong();
        mockMvc.perform(post("/api/v1/members/" + dupId + "/set-login")
                        .header("Authorization", "Bearer " + hostAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"password123\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void memberSeesOnlyOwnGroupsAndShares() throws Exception {
        MemberHost host = setupHostGroup();
        long memberId = host.memberIds().get(0);
        String memberToken = memberLoginToken(awaitLogin(memberId));

        JsonNode groups = myGroups(memberToken);
        assertThat(groups).hasSize(1);
        JsonNode group = groups.path(0);
        assertThat(group.path("id").asLong()).isEqualTo(host.groupId());
        assertThat(group.path("currency").asText()).isEqualTo("VND");
        assertThat(group.path("myShareCount").asInt()).isEqualTo(1);
        assertThat(group.path("myShares")).hasSize(1);

        // hostB's group is invisible to this member
        long otherGroup = createGroup(hostBToken);
        mockMvc.perform(get("/api/v1/me/groups/" + otherGroup)
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/me/groups/999999")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isNotFound());

        // own group detail and empty balance preview
        MvcResult detail = mockMvc.perform(get("/api/v1/me/groups/" + host.groupId())
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk()).andReturn();
        assertThat(objectMapper.readTree(detail.getResponse().getContentAsString())
                .path("myShareCount").asInt()).isEqualTo(1);

        JsonNode balance = objectMapper.readTree(mockMvc.perform(get("/api/v1/me/groups/" + host.groupId() + "/balance")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(balance.path("contributedMinor").asLong()).isZero();
        assertThat(balance.path("receivedMinor").asLong()).isZero();
        assertThat(balance.path("netPositionMinor").asLong()).isZero();
    }

    @Test
    void balanceReflectsContributionAndPayout() throws Exception {
        MemberHost host = setupHostGroup();
        long memberPayer = host.memberIds().get(1);
        long memberWinner = host.memberIds().get(0);
        String payerToken = memberLoginToken(awaitLogin(memberPayer));
        String winnerToken = memberLoginToken(awaitLogin(memberWinner));

        long cycleId = openCycle(hostAToken, host.groupId());
        long winnerShareId = shareOf(host.memberIds().get(0));
        long payerShareId = shareOf(memberPayer);
        mockMvc.perform(post("/api/v1/cycles/" + cycleId + "/bids")
                        .header("Authorization", "Bearer " + hostAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shareId\":%d,\"amountMinor\":200000}".formatted(winnerShareId)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/cycles/" + cycleId + "/close-and-calculate")
                        .header("Authorization", "Bearer " + hostAToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/cycles/" + cycleId + "/confirm-payout")
                        .header("Authorization", "Bearer " + hostAToken))
                .andExpect(status().isOk());

        // winner saw a PAID payout
        JsonNode winnerBalance = objectMapper.readTree(mockMvc.perform(
                        get("/api/v1/me/groups/" + host.groupId() + "/balance").header("Authorization", "Bearer " + winnerToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(winnerBalance.path("receivedMinor").asLong()).isEqualTo(1_600_000);
        assertThat(winnerBalance.path("netPositionMinor").asLong()).isEqualTo(1_600_000);

        // payer still owes: 0 contributed until host records their cash
        JsonNode before = objectMapper.readTree(mockMvc.perform(
                        get("/api/v1/me/groups/" + host.groupId() + "/balance").header("Authorization", "Bearer " + payerToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(before.path("contributedMinor").asLong()).isZero();

        long payerEntry = contributionEntry(cycleId, payerShareId);
        mockMvc.perform(post("/api/v1/groups/" + host.groupId() + "/payments")
                        .header("Authorization", "Bearer " + hostAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountMinor\":800000,\"currency\":\"VND\",\"allocations\":" +
                                "[{\"ledgerEntryId\":%d,\"amountMinor\":800000}]}".formatted(payerEntry)))
                .andExpect(status().isCreated());

        JsonNode after = objectMapper.readTree(mockMvc.perform(
                        get("/api/v1/me/groups/" + host.groupId() + "/balance").header("Authorization", "Bearer " + payerToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(after.path("contributedMinor").asLong()).isEqualTo(800_000);
        assertThat(after.path("receivedMinor").asLong()).isZero();
        assertThat(after.path("netPositionMinor").asLong()).isEqualTo(-800_000);
        JsonNode share = after.path("shares").path(0);
        assertThat(share.path("contributedMinor").asLong()).isEqualTo(800_000);
        assertThat(share.path("netPositionMinor").asLong()).isEqualTo(-800_000);

        // a group the member does not belong to cannot be read
        mockMvc.perform(get("/api/v1/me/groups/999999/balance")
                        .header("Authorization", "Bearer " + payerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void memberBidsOwnShareOnly() throws Exception {
        MemberHost host = setupHostGroup();
        long memberId = host.memberIds().get(0);
        String token = memberLoginToken(awaitLogin(memberId));
        long cycleId = openCycle(hostAToken, host.groupId());
        long myShare = shareOf(memberId);
        long otherShare = shareOf(host.memberIds().get(1));

        MvcResult bid = mockMvc.perform(post("/api/v1/me/cycles/" + cycleId + "/bids")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shareId\":%d,\"amountMinor\":250000}".formatted(myShare)))
                .andExpect(status().isCreated()).andReturn();
        JsonNode body = objectMapper.readTree(bid.getResponse().getContentAsString());
        assertThat(body.path("shareId").asLong()).isEqualTo(myShare);
        assertThat(body.path("amountMinor").asLong()).isEqualTo(250_000);

        mockMvc.perform(post("/api/v1/me/cycles/" + cycleId + "/bids")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shareId\":%d,\"amountMinor\":200000}".formatted(otherShare)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/me/cycles/" + cycleId + "/bids")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shareId\":%d,\"amountMinor\":500}".formatted(myShare)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void portalRequiresMemberRoleAndAuth() throws Exception {
        long groupId = createGroup(hostAToken);
        mockMvc.perform(get("/api/v1/me/groups").header("Authorization", "Bearer " + hostAToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/me/groups"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/me/cycles/1/bids"))
                .andExpect(status().isUnauthorized());
        assertThat(groupId).isPositive();
    }

    /** After setupHostGroup the member needs a login; provision + log in is async-safe here. */
    private long awaitLogin(long memberId) throws Exception {
        MvcResult set = mockMvc.perform(post("/api/v1/members/" + memberId + "/set-login")
                        .header("Authorization", "Bearer " + hostAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"password123\"}"))
                .andExpect(status().isOk()).andReturn();
        assertThat(objectMapper.readTree(set.getResponse().getContentAsString()).path("loginEnabled").asBoolean()).isTrue();
        return memberId;
    }

    private long shareOf(long memberId) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM group_shares WHERE member_profile_id = ?", Long.class, memberId);
    }
}
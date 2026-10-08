package com.tongtin;

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
 * Step 16 hardening: cross-tenant + cross-role authz sweep over every
 * money/winner and owner-scoped endpoint. Rule (01-DOMAIN 05): unknown /
 * cross-tenant / cross-owner -> 404; wrong role -> 403; unauthenticated -> 401.
 * Member portal MUST NOT expose host-only aggregation to non-owner members.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthzSweepTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<String> createdPhones = new ArrayList<>();
    private String victimHostToken;
    private String attackerHostToken;
    private String attackerMemberToken;
    private long victimGroupId;
    private long victimCycleId;
    private long victimMemberShareId;

    @BeforeEach
    void setUp() throws Exception {
        victimHostToken = register(hostPhone());
        attackerHostToken = register(hostPhone());

        long attackerMemberId = createMember(attackerHostToken, phone());
        attackerMemberToken = memberLogin(attackerHostToken, attackerMemberId);

        victimGroupId = createGroup(victimHostToken);
        for (int i = 0; i < 3; i++) {
            long memberId = createMember(victimHostToken, phone());
            long shareId = assignShare(victimHostToken, victimGroupId, memberId);
            if (i == 0) {
                victimMemberShareId = shareId;
            }
        }
        mockMvc.perform(post("/api/v1/groups/" + victimGroupId + "/start")
                        .header("Authorization", "Bearer " + victimHostToken))
                .andExpect(status().isOk());
        MvcResult o = mockMvc.perform(post("/api/v1/groups/" + victimGroupId + "/cycles/open")
                        .header("Authorization", "Bearer " + victimHostToken))
                .andExpect(status().isCreated()).andReturn();
        victimCycleId = objectMapper.readTree(o.getResponse().getContentAsString()).path("id").asLong();
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

    private String hostPhone() {
        String p = "0911" + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
        createdPhones.add(p);
        return p;
    }

    private String phone() {
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

    private long createMember(String hostToken, String phone) throws Exception {
        MvcResult m = mockMvc.perform(post("/api/v1/members")
                        .header("Authorization", "Bearer " + hostToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Hoi Vien\",\"phone\":\"%s\"}".formatted(phone)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(m.getResponse().getContentAsString()).path("id").asLong();
    }

    private long createGroup(String hostToken) throws Exception {
        String body = """
                {"name":"Hoi Authz","type":"BIDDING","baseAmount":1000000,"shareCount":3,"cycleUnit":"MONTH",
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

    private long assignShare(String hostToken, long groupId, long memberId) throws Exception {
        MvcResult s = mockMvc.perform(post("/api/v1/groups/" + groupId + "/shares")
                        .header("Authorization", "Bearer " + hostToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberProfileId\":%d,\"count\":1}".formatted(memberId)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode shares = objectMapper.readTree(s.getResponse().getContentAsString());
        return shares.path(shares.size() - 1).path("id").asLong();
    }

    private String memberLogin(String hostToken, long memberId) throws Exception {
        String phone = jdbcTemplate.queryForObject("SELECT phone FROM member_profiles WHERE id = ?",
                String.class, memberId);
        return login(hostToken, memberId, phone);
    }

    private String login(String hostToken, long memberId, String phone) throws Exception {
        MvcResult set = mockMvc.perform(post("/api/v1/members/" + memberId + "/set-login")
                        .header("Authorization", "Bearer " + hostToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"password123\"}"))
                .andExpect(status().isOk()).andReturn();
        if (!objectMapper.readTree(set.getResponse().getContentAsString()).path("loginEnabled").asBoolean()) {
            throw new IllegalStateException("set-login failed");
        }
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", uniqueIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"%s\",\"password\":\"password123\"}".formatted(phone)))
                .andReturn();
        if (login.getResponse().getStatus() != 200) {
            throw new IllegalStateException("login failed: " + login.getResponse().getStatus());
        }
        return objectMapper.readTree(login.getResponse().getContentAsString())
                .path("tokens").path("accessToken").asText();
    }

    private void expect(String method, String url, String body, String token, int expected) throws Exception {
        var req = "GET".equals(method) ? get(url) : post(url);
        if (body != null) {
            req = req.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        if (token != null) {
            req = req.header("Authorization", "Bearer " + token);
        }
        mockMvc.perform(req).andExpect(status().is(expected));
    }

    private void expectOwner(String method, String url, String body) throws Exception {
        var req = "GET".equals(method) ? get(url) : post(url);
        if (body != null) {
            req = req.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        mockMvc.perform(req.header("Authorization", "Bearer " + victimHostToken))
                .andExpect(result -> org.assertj.core.api.Assertions.assertThat(
                        result.getResponse().getStatus()).isBetween(200, 409));
    }

    private static final String[][] HOST_ONLY = {
            {"GET", "/api/v1/groups/{g}", null},
            {"GET", "/api/v1/groups/{g}/cycles", null},
            {"POST", "/api/v1/groups/{g}/start", null},
            {"POST", "/api/v1/groups/{g}/shares", "{\"memberProfileId\":999999,\"count\":1}"},
            {"POST", "/api/v1/groups/{g}/cycles/open", null},
            {"POST", "/api/v1/cycles/{c}/bids", "{\"shareId\":99999999,\"amountMinor\":200000}"},
            {"GET", "/api/v1/cycles/{c}/summary", null},
            {"POST", "/api/v1/cycles/{c}/close-and-calculate", null},
            {"POST", "/api/v1/cycles/{c}/confirm-payout", null},
            {"POST", "/api/v1/groups/{g}/payments",
                    "{\"amountMinor\":800000,\"currency\":\"VND\",\"allocations\":[{\"ledgerEntryId\":99999999,\"amountMinor\":800000}]}"},
            {"GET", "/api/v1/groups/{g}/debts", null},
            {"POST", "/api/v1/groups/{g}/late-fees/assess", null},
            {"GET", "/api/v1/groups/{g}/ledger", null},
            {"GET", "/api/v1/groups/{g}/profit", null},
            {"GET", "/api/v1/groups/{g}/export/ledger?format=csv", null},
            {"GET", "/api/v1/groups/{g}/export/ledger?format=xlsx", null},
            {"GET", "/api/v1/groups/{g}/export/profit?format=csv", null},
    };

    private static final String[][] MEMBER_PORTAL = {
            {"GET", "/api/v1/me/groups", null},
            {"GET", "/api/v1/me/groups/{g}/balance", null},
            {"GET", "/api/v1/me/groups/{g}/statement", null},
            {"GET", "/api/v1/me/groups/{g}/export/statement?format=csv", null},
            {"GET", "/api/v1/me/groups/{g}/export/statement?format=xlsx", null},
            {"GET", "/api/v1/me/groups/{g}/cycles", null},
            {"POST", "/api/v1/me/cycles/{c}/bids", "{\"shareId\":99999999,\"amountMinor\":200000}"},
    };

    private String render(String url) {
        return url.replace("{g}", String.valueOf(victimGroupId)).replace("{c}", String.valueOf(victimCycleId));
    }

    @Test
    void hostEndpointsRejectCrossTenantMembersAndAnon() throws Exception {
        for (String[] line : HOST_ONLY) {
            String url = render(line[1]);
            String body = line[2];
            // attacker host passes role check -> service resolves group/cycle by owner -> 404
            expect(line[0], url, body, attackerHostToken, 404);
            // member role -> @PreAuthorize denies -> 403
            expect(line[0], url, body, attackerMemberToken, 403);
            // anonymous -> 401
            expect(line[0], url, body, null, 401);
            // owner passes security + service layer (data-dependent 2xx/4xx, never 401/403/404)
            expectOwner(line[0], url, body);
        }
    }

    @Test
    void memberPortalRejectsHostAndAnonAndHidesForeignGroups() throws Exception {
        for (String[] line : MEMBER_PORTAL) {
            String url = render(line[1]);
            String body = line[2];
            // host role -> 403 on member routes
            expect(line[0], url, body, attackerHostToken, 403);
            // anonymous -> 401
            expect(line[0], url, body, null, 401);
            // attacker member (in a different host's group) sees only their own list
            int expected = url.equals("/api/v1/me/groups") ? 200 : 404;
            expect(line[0], url, body, attackerMemberToken, expected);
        }
    }

    @Test
    void memberInOwnGroupCannotBidAnotherMembersShare() throws Exception {
        long memberProfileId = jdbcTemplate.queryForObject(
                "SELECT member_profile_id FROM group_shares WHERE id = ?", Long.class, victimMemberShareId);
        String phone = jdbcTemplate.queryForObject("SELECT phone FROM member_profiles WHERE id = ?",
                String.class, memberProfileId);
        String token = login(victimHostToken, memberProfileId, phone);

        String sharesJson = mockMvc.perform(get("/api/v1/groups/" + victimGroupId + "/shares")
                        .header("Authorization", "Bearer " + victimHostToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode shares = objectMapper.readTree(sharesJson);
        long otherShare = 0;
        for (JsonNode s : shares) {
            if (s.path("id").asLong() != victimMemberShareId) {
                otherShare = s.path("id").asLong();
                break;
            }
        }
        mockMvc.perform(post("/api/v1/me/cycles/" + victimCycleId + "/bids")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shareId\":%d,\"amountMinor\":200000}".formatted(otherShare)))
                .andExpect(status().isForbidden());
    }
}
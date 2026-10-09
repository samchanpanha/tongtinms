package com.tongtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Step 33: owner-local phone blacklist (V16) + enforced member status.
 * BLOCKED -> no login (and no set-login); INACTIVE/BLOCKED -> no bid;
 * blacklisted phone -> 409 on add-member / add-share. Scope is one owner.
 */
@SpringBootTest
@AutoConfigureMockMvc
class BlacklistTests {

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
        hostAPhone = "0902" + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
        hostBPhone = "0903" + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
        hostAToken = registerHost(hostAPhone);
        hostBToken = registerHost(hostBPhone);
    }

    @AfterEach
    void cleanUp() {
        String filter = "phone IN ('" + hostAPhone + "','" + hostBPhone + "','+84"
                + hostAPhone.substring(1) + "','+84" + hostBPhone.substring(1) + "')";
        String ownerIds = "(SELECT id FROM owner_accounts WHERE user_id IN (SELECT id FROM users WHERE " + filter + "))";
        String groupIds = "(SELECT id FROM groups WHERE owner_id IN " + ownerIds + ")";
        String cycleIds = "(SELECT c.id FROM cycles c WHERE c.group_id IN " + groupIds + ")";
        String ledgerIds = "(SELECT l.id FROM ledger_entries l WHERE l.cycle_id IN " + cycleIds + ")";
        jdbcTemplate.update("DELETE FROM member_blacklists WHERE owner_id IN " + ownerIds);
        jdbcTemplate.update("DELETE FROM audit_events WHERE actor_user_id IN (SELECT id FROM users WHERE " + filter + ")");
        jdbcTemplate.update("DELETE FROM payment_allocations WHERE ledger_entry_id IN " + ledgerIds);
        jdbcTemplate.update("DELETE FROM payments WHERE group_id IN " + groupIds);
        jdbcTemplate.update("DELETE FROM ledger_entries WHERE cycle_id IN " + cycleIds);
        jdbcTemplate.update("DELETE FROM bids WHERE cycle_id IN " + cycleIds);
        jdbcTemplate.update("UPDATE group_shares SET won_cycle_id = NULL WHERE group_id IN " + groupIds);
        jdbcTemplate.update("DELETE FROM cycles WHERE group_id IN " + groupIds);
        jdbcTemplate.update("DELETE FROM group_shares WHERE group_id IN " + groupIds);
        jdbcTemplate.update("DELETE FROM groups WHERE owner_id IN " + ownerIds);
        jdbcTemplate.update("DELETE FROM member_profiles WHERE owner_id IN " + ownerIds);
        jdbcTemplate.update("DELETE FROM notifications WHERE user_id IN (SELECT id FROM users WHERE " + filter + ")");
        jdbcTemplate.update("DELETE FROM owner_accounts WHERE user_id IN (SELECT id FROM users WHERE " + filter + ")");
        jdbcTemplate.update("DELETE FROM user_roles WHERE user_id IN (SELECT id FROM users WHERE " + filter + ")");
        jdbcTemplate.update("DELETE FROM users WHERE " + filter);
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

    private String uniqueIp() {
        return "10.31." + ThreadLocalRandom.current().nextInt(256) + "." + ThreadLocalRandom.current().nextInt(256);
    }

    private String randomPhone(String prefix) {
        return prefix + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
    }

    private long createMember(String token, String phone) throws Exception {
        MvcResult m = mockMvc.perform(post("/api/v1/members")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Hoi Vien\",\"phone\":\"%s\"}".formatted(phone)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(m.getResponse().getContentAsString()).path("id").asLong();
    }

    private long blacklist(String token, String phone, String reason) throws Exception {
        String body = "{\"phone\":\"%s\"%s}".formatted(phone,
                reason == null ? "" : ",\"reason\":\"" + reason + "\"");
        MvcResult result = mockMvc.perform(post("/api/v1/members/blacklist")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("id").asLong();
    }

    private long createGroup(String token, int shareCount) throws Exception {
        String body = """
                {"name":"Hoi Blacklist","type":"BIDDING","baseAmount":1000000,"shareCount":%d,"cycleUnit":"MONTH",
                 "cycleCount":%d,"currency":"VND","maxBid":500000,"bidStep":10000,"bidCloseOffset":7}
                """.formatted(shareCount, shareCount);
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

    private long openRunningGroupWithCycle(String token, long firstMemberId) throws Exception {
        // 3-member group so it can start; first member holds one share.
        long groupId = createGroup(token, 3);
        assignShare(token, groupId, firstMemberId);
        for (int i = 0; i < 2; i++) {
            assignShare(token, groupId, createMember(token, randomPhone("0967")));
        }
        mockMvc.perform(post("/api/v1/groups/" + groupId + "/start")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        return groupId;
    }

    private long openCycle(String token, long groupId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/groups/" + groupId + "/cycles/open")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("id").asLong();
    }

    private void patchStatus(String token, long memberId, String status) throws Exception {
        mockMvc.perform(patch("/api/v1/members/" + memberId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"" + status + "\"}"))
                .andExpect(status().isOk());
    }

    private int loginStatus(String phone) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", uniqueIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"%s\",\"password\":\"password123\"}".formatted(phone)))
                .andReturn().getResponse().getStatus();
    }

    @Test
    void addListDuplicateUnlistAndReactivate() throws Exception {
        String phone = randomPhone("0920");
        long id = blacklist(hostAToken, phone, "defaulted in HOI-2025-003");

        JsonNode list = objectMapper.readTree(mockMvc.perform(get("/api/v1/members/blacklist")
                        .header("Authorization", "Bearer " + hostAToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(list).hasSize(1);
        assertThat(list.path(0).path("phone").asText()).isEqualTo("+84" + phone.substring(1));
        assertThat(list.path(0).path("active").asBoolean()).isTrue();
        assertThat(list.path(0).path("reason").asText()).contains("defaulted");

        mockMvc.perform(post("/api/v1/members/blacklist")
                        .header("Authorization", "Bearer " + hostAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"%s\"}".formatted(phone)))
                .andExpect(status().isConflict());

        mockMvc.perform(delete("/api/v1/members/blacklist/" + id)
                        .header("Authorization", "Bearer " + hostAToken))
                .andExpect(status().isOk());

        JsonNode afterUnlist = objectMapper.readTree(mockMvc.perform(get("/api/v1/members/blacklist")
                        .header("Authorization", "Bearer " + hostAToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(afterUnlist).hasSize(1);
        assertThat(afterUnlist.path(0).path("active").asBoolean()).isFalse();

        long id2 = blacklist(hostAToken, phone, null);
        assertThat(id2).isEqualTo(id);
        JsonNode reactivated = objectMapper.readTree(mockMvc.perform(get("/api/v1/members/blacklist")
                        .header("Authorization", "Bearer " + hostAToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(reactivated.path(0).path("active").asBoolean()).isTrue();
    }

    @Test
    void blacklistedPhoneRejectedForMemberAndShare() throws Exception {
        String blockedPhone = randomPhone("0921");
        blacklist(hostAToken, blockedPhone, "bad payer");

        MvcResult rejected = mockMvc.perform(post("/api/v1/members")
                        .header("Authorization", "Bearer " + hostAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Nope\",\"phone\":\"%s\"}".formatted(blockedPhone)))
                .andExpect(status().isConflict())
                .andReturn();
        assertThat(rejected.getResponse().getContentAsString()).contains("bad payer");

        long memberId = createMember(hostAToken, randomPhone("0922"));
        String memberPhone = jdbcTemplate.queryForObject(
                "SELECT phone FROM member_profiles WHERE id = ?", String.class, memberId);
        blacklist(hostAToken, memberPhone, "now blocked");

        long groupId = createGroup(hostAToken, 3);
        mockMvc.perform(post("/api/v1/groups/" + groupId + "/shares")
                        .header("Authorization", "Bearer " + hostAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberProfileId\":%d,\"count\":1}".formatted(memberId)))
                .andExpect(status().isConflict());
    }

    @Test
    void blockedMemberCannotLoginNorBeGivenLogin() throws Exception {
        String phone = randomPhone("0923");
        long memberId = createMember(hostAToken, phone);

        mockMvc.perform(post("/api/v1/members/" + memberId + "/set-login")
                        .header("Authorization", "Bearer " + hostAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"password123\"}"))
                .andExpect(status().isOk());
        assertThat(loginStatus(phone)).isEqualTo(200);

        patchStatus(hostAToken, memberId, "BLOCKED");
        assertThat(loginStatus(phone)).isEqualTo(401);

        mockMvc.perform(post("/api/v1/members/" + memberId + "/set-login")
                        .header("Authorization", "Bearer " + hostAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"password123\"}"))
                .andExpect(status().isConflict());

        patchStatus(hostAToken, memberId, "ACTIVE");
        assertThat(loginStatus(phone)).isEqualTo(200);
    }

    @Test
    void inactiveMemberCannotBidOnHostOrMemberRoute() throws Exception {
        String phone = randomPhone("0924");
        long memberId = createMember(hostAToken, phone);
        long groupId = openRunningGroupWithCycle(hostAToken, memberId);
        long cycleId = openCycle(hostAToken, groupId);
        long shareId = jdbcTemplate.queryForObject(
                "SELECT id FROM group_shares WHERE member_profile_id = ?", Long.class, memberId);

        // host route works while ACTIVE
        mockMvc.perform(post("/api/v1/cycles/" + cycleId + "/bids")
                        .header("Authorization", "Bearer " + hostAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shareId\":%d,\"amountMinor\":200000}".formatted(shareId)))
                .andExpect(status().isCreated());

        patchStatus(hostAToken, memberId, "INACTIVE");
        mockMvc.perform(post("/api/v1/cycles/" + cycleId + "/bids")
                        .header("Authorization", "Bearer " + hostAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shareId\":%d,\"amountMinor\":210000}".formatted(shareId)))
                .andExpect(status().isBadRequest());

        // member portal: INACTIVE still allows login to view, but not to bid
        mockMvc.perform(post("/api/v1/members/" + memberId + "/set-login")
                        .header("Authorization", "Bearer " + hostAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"password123\"}"))
                .andExpect(status().isOk());
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", uniqueIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"%s\",\"password\":\"password123\"}".formatted(phone)))
                .andExpect(status().isOk())
                .andReturn();
        String memberToken = objectMapper.readTree(login.getResponse().getContentAsString())
                .path("tokens").path("accessToken").asText();
        mockMvc.perform(post("/api/v1/me/cycles/" + cycleId + "/bids")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shareId\":%d,\"amountMinor\":220000}".formatted(shareId)))
                .andExpect(status().isBadRequest());

        patchStatus(hostAToken, memberId, "ACTIVE");
        mockMvc.perform(post("/api/v1/cycles/" + cycleId + "/bids")
                        .header("Authorization", "Bearer " + hostAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shareId\":%d,\"amountMinor\":230000}".formatted(shareId)))
                .andExpect(status().isCreated());
    }

    @Test
    void crossOwnerAndRoleGuards() throws Exception {
        long id = blacklist(hostAToken, randomPhone("0925"), null);

        // attacker host cannot unlist another owner's entry
        mockMvc.perform(delete("/api/v1/members/blacklist/" + id)
                        .header("Authorization", "Bearer " + hostBToken))
                .andExpect(status().isNotFound());

        // attacker host sees its own (empty) list, not the victim's
        JsonNode attackerList = objectMapper.readTree(mockMvc.perform(get("/api/v1/members/blacklist")
                        .header("Authorization", "Bearer " + hostBToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(attackerList).isEmpty();

        // member token -> 403 (host-only), anonymous -> 401
        String memberPhone = randomPhone("0926");
        long memberId = createMember(hostBToken, memberPhone);
        MvcResult setLogin = mockMvc.perform(post("/api/v1/members/" + memberId + "/set-login")
                        .header("Authorization", "Bearer " + hostBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"password123\"}"))
                .andExpect(status().isOk()).andReturn();
        assertThat(setLogin.getResponse().getStatus()).isEqualTo(200);
        String memberToken = objectMapper.readTree(mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", uniqueIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"%s\",\"password\":\"password123\"}".formatted(memberPhone)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString())
                .path("tokens").path("accessToken").asText();

        mockMvc.perform(get("/api/v1/members/blacklist")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/members/blacklist")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"%s\"}".formatted(randomPhone("0927"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/members/blacklist"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/members/blacklist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"0928123456\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/members/blacklist/" + id))
                .andExpect(status().isUnauthorized());
    }
}

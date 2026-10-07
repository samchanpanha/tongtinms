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
class NotificationTests {

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

    private long createGroup(String type, int shareCount) throws Exception {
        String body = """
                {"name":"Hoi Thong Bao","type":"%s","baseAmount":1000000,"shareCount":%d,"cycleUnit":"MONTH",
                 "cycleCount":%d,"currency":"VND","maxBid":500000,"bidStep":10000,"bidCloseOffset":7}
                """.formatted(type, shareCount, shareCount);
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

    private record Fixture(long groupId, List<Long> memberIds, List<Long> shareIds) {
    }

    private Fixture setupBiddingGroup() throws Exception {
        long groupId = createGroup("BIDDING", 3);
        List<Long> memberIds = new ArrayList<>();
        List<Long> shareIds = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            long member = createMember();
            memberIds.add(member);
            shareIds.add(assignShare(groupId, member));
        }
        mockMvc.perform(post("/api/v1/groups/" + groupId + "/start")
                        .header("Authorization", "Bearer " + hostToken))
                .andExpect(status().isOk());
        return new Fixture(groupId, memberIds, shareIds);
    }

    private long openCycle(long groupId) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/v1/groups/" + groupId + "/cycles/open")
                        .header("Authorization", "Bearer " + hostToken))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).path("id").asLong();
    }

    private String memberToken(long memberId) throws Exception {
        String phone = jdbcTemplate.queryForObject(
                "SELECT phone FROM member_profiles WHERE id = ?", String.class, memberId);
        return login(phone, memberId, true);
    }

    private String login(String phone, long memberId, boolean provision) throws Exception {
        if (provision) {
            MvcResult set = mockMvc.perform(post("/api/v1/members/" + memberId + "/set-login")
                            .header("Authorization", "Bearer " + hostToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"password\":\"password123\"}"))
                    .andExpect(status().isOk()).andReturn();
            assertThat(objectMapper.readTree(set.getResponse().getContentAsString())
                    .path("loginEnabled").asBoolean()).isTrue();
        }
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", uniqueIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"%s\",\"password\":\"password123\"}".formatted(phone)))
                .andReturn();
        assertThat(login.getResponse().getStatus()).isEqualTo(200);
        return objectMapper.readTree(login.getResponse().getContentAsString())
                .path("tokens").path("accessToken").asText();
    }

    private JsonNode feed(String token) throws Exception {
        MvcResult r = mockMvc.perform(get("/api/v1/notifications")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString());
    }

    private long closeAndConfirm(Long cycleId) throws Exception {
        mockMvc.perform(post("/api/v1/cycles/" + cycleId + "/close-and-calculate")
                        .header("Authorization", "Bearer " + hostToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/cycles/" + cycleId + "/confirm-payout")
                        .header("Authorization", "Bearer " + hostToken))
                .andExpect(status().isOk());
        return 0;
    }

    @Test
    void cycleOpenedNotifiesMembersWithLoginButNotHost() throws Exception {
        Fixture f = setupBiddingGroup();
        String m1 = memberToken(f.memberIds().get(0));
        String m2 = memberToken(f.memberIds().get(1));
        openCycle(f.groupId());

        JsonNode feed1 = feed(m1);
        assertThat(feed1.path("unreadCount").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(types(feed1)).contains("CYCLE_OPENED");
        for (JsonNode n : feed1.path("notifications")) {
            if ("CYCLE_OPENED".equals(n.path("type").asText())) {
                assertThat(n.path("title").asText()).contains("Ky");
            }
        }

        // every logged-in member got exactly one CYCLE_OPENED for this open
        JsonNode feed2 = feed(m2);
        long opened = 0;
        for (JsonNode n : feed2.path("notifications")) {
            if ("CYCLE_OPENED".equals(n.path("type").asText())) {
                opened++;
            }
        }
        assertThat(opened).isEqualTo(1);

        // member without login never gets rows: only MEMBER_LOGIN_NOTICE + CYCLE_OPENED here
        assertThat(types(feed2)).containsExactlyInAnyOrder("MEMBER_LOGIN_NOTICE", "CYCLE_OPENED");

        // host gets nothing for their own action
        JsonNode hostFeed = feed(hostToken);
        assertThat(hostFeed.path("notifications")).isEmpty();
        assertThat(hostFeed.path("unreadCount").asLong()).isZero();

        // anonymous cannot read the feed
        mockMvc.perform(get("/api/v1/notifications")).andExpect(status().isUnauthorized());
    }

    @Test
    void closeAndConfirmPublishWinnerAndPayout() throws Exception {
        Fixture f = setupBiddingGroup();
        String m1 = memberToken(f.memberIds().get(0));
        String m2 = memberToken(f.memberIds().get(1));
        long cycleId = openCycle(f.groupId());

        // host bids for share 1 (the eventual winner)
        mockMvc.perform(post("/api/v1/cycles/" + cycleId + "/bids")
                        .header("Authorization", "Bearer " + hostToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shareId\":%d,\"amountMinor\":200000}".formatted(f.shareIds().get(0))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/cycles/" + cycleId + "/close-and-calculate")
                        .header("Authorization", "Bearer " + hostToken))
                .andExpect(status().isOk());

        assertThat(types(feed(m1))).contains("WINNER_PUBLISHED");
        assertThat(types(feed(m2))).contains("WINNER_PUBLISHED");

        mockMvc.perform(post("/api/v1/cycles/" + cycleId + "/confirm-payout")
                        .header("Authorization", "Bearer " + hostToken))
                .andExpect(status().isOk());

        // winner (share 1 member) got PAYOUT_READY; another member did not
        assertThat(types(feed(m1))).contains("PAYOUT_READY");
        assertThat(types(feed(m2))).doesNotContain("PAYOUT_READY");
        JsonNode winnerFeed = feed(m1);
        for (JsonNode n : winnerFeed.path("notifications")) {
            if ("PAYOUT_READY".equals(n.path("type").asText())) {
                assertThat(n.path("body").asText()).contains("1_600_000".replace("_", ""));
            }
        }
    }

    @Test
    void groupCompletedNotifiesAllWhenLastCycleConfirmed() throws Exception {
        long groupId = createGroup("FIXED", 2);
        List<Long> memberIds = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            memberIds.add(createMember());
            assignShare(groupId, memberIds.get(i));
        }
        mockMvc.perform(post("/api/v1/groups/" + groupId + "/start")
                        .header("Authorization", "Bearer " + hostToken))
                .andExpect(status().isOk());
        String m1 = memberToken(memberIds.get(0));

        long cycle1 = openCycle(groupId);
        closeAndConfirm(cycle1);
        long cycle2 = openCycle(groupId);
        closeAndConfirm(cycle2);

        assertThat(types(feed(m1))).contains("GROUP_COMPLETED");
        JsonNode g = objectMapper.readTree(mockMvc.perform(get("/api/v1/me/groups/" + groupId)
                        .header("Authorization", "Bearer " + m1))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(g.path("status").asText()).isEqualTo("COMPLETED");
    }

    @Test
    void paymentConfirmedNotifiesPayerMember() throws Exception {
        Fixture f = setupBiddingGroup();
        String payer = memberToken(f.memberIds().get(1));
        long cycleId = openCycle(f.groupId());

        mockMvc.perform(post("/api/v1/cycles/" + cycleId + "/bids")
                        .header("Authorization", "Bearer " + hostToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shareId\":%d,\"amountMinor\":200000}".formatted(f.shareIds().get(0))))
                .andExpect(status().isCreated());
        closeAndConfirm(cycleId);

        long payerEntry = jdbcTemplate.queryForObject(
                "SELECT id FROM ledger_entries WHERE cycle_id = ? AND share_id = ? AND type = 'CONTRIBUTION'",
                Long.class, cycleId, f.shareIds().get(1));
        mockMvc.perform(post("/api/v1/groups/" + f.groupId() + "/payments")
                        .header("Authorization", "Bearer " + hostToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountMinor\":800000,\"currency\":\"VND\",\"allocations\":" +
                                "[{\"ledgerEntryId\":%d,\"amountMinor\":800000}]}".formatted(payerEntry)))
                .andExpect(status().isCreated());

        assertThat(types(feed(payer))).contains("PAYMENT_CONFIRMED");
        // the same member should not have PAYOUT_READY since they did not win
        assertThat(types(feed(payer))).doesNotContain("PAYOUT_READY");
    }

    @Test
    void setLoginCreatesLoginNotice() throws Exception {
        long memberId = createMember();
        String token = memberToken(memberId);
        assertThat(types(feed(token))).contains("MEMBER_LOGIN_NOTICE");
    }

    @Test
    void feedIsOwnedAndReadActionsWork() throws Exception {
        Fixture f = setupBiddingGroup();
        String m1 = memberToken(f.memberIds().get(0));
        String m2 = memberToken(f.memberIds().get(1));

        JsonNode before = feed(m1);
        long unread = before.path("unreadCount").asLong();
        long firstId = before.path("notifications").path(0).path("id").asLong();

        // marking my own row read clears readAt + decrements unread
        mockMvc.perform(post("/api/v1/notifications/" + firstId + "/read")
                        .header("Authorization", "Bearer " + m1))
                .andExpect(status().isOk());
        assertThat(feed(m1).path("unreadCount").asLong()).isEqualTo(unread - 1);

        // another member cannot mark my row read
        mockMvc.perform(post("/api/v1/notifications/" + firstId + "/read")
                        .header("Authorization", "Bearer " + m2))
                .andExpect(status().isNotFound());

        // read-all clears everything
        MvcResult all = mockMvc.perform(post("/api/v1/notifications/read-all")
                        .header("Authorization", "Bearer " + m1))
                .andExpect(status().isOk()).andReturn();
        assertThat(objectMapper.readTree(all.getResponse().getContentAsString()).path("updatedCount").asLong())
                .isEqualTo(unread - 1);
        assertThat(feed(m1).path("unreadCount").asLong()).isZero();

        // unread-count endpoint agrees
        MvcResult count = mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .header("Authorization", "Bearer " + m1))
                .andExpect(status().isOk()).andReturn();
        assertThat(objectMapper.readTree(count.getResponse().getContentAsString())
                .path("unreadCount").asLong()).isZero();
    }

    private static List<String> types(JsonNode feed) {
        List<String> types = new ArrayList<>();
        for (JsonNode n : feed.path("notifications")) {
            types.add(n.path("type").asText());
        }
        return types;
    }
}
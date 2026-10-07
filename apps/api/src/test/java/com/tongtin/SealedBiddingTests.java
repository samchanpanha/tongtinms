package com.tongtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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

@SpringBootTest
@AutoConfigureMockMvc
class SealedBiddingTests {

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
        jdbcTemplate.update("DELETE FROM audit_events WHERE actor_user_id IN (SELECT id FROM users WHERE " + filter + ")");
        jdbcTemplate.update("DELETE FROM bids WHERE cycle_id IN (SELECT c.id FROM cycles c WHERE c.group_id IN (SELECT id FROM groups WHERE owner_id IN (SELECT id FROM owner_accounts WHERE user_id IN (SELECT id FROM users WHERE " + filter + "))))");
        jdbcTemplate.update("DELETE FROM cycles WHERE group_id IN (SELECT id FROM groups WHERE owner_id IN (SELECT id FROM owner_accounts WHERE user_id IN (SELECT id FROM users WHERE " + filter + ")))");
        jdbcTemplate.update("DELETE FROM group_shares WHERE group_id IN (SELECT id FROM groups WHERE owner_id IN (SELECT id FROM owner_accounts WHERE user_id IN (SELECT id FROM users WHERE " + filter + ")))");
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
        return "10.14." + ThreadLocalRandom.current().nextInt(256) + "." + ThreadLocalRandom.current().nextInt(256);
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

    private long createStartedGroup(String token, int shareCount) throws Exception {
        String groupBody = """
                {"name":"Hoi Dau Gia","type":"BIDDING","baseAmount":1000000,"shareCount":%d,"cycleUnit":"MONTH",
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

    private JsonNode openCycle(String token, long groupId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/groups/" + groupId + "/cycles/open")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode firstShareOf(String token, long groupId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/groups/" + groupId + "/shares")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path(0);
    }

    private MvcResult submitBid(String token, long cycleId, long shareId, long amount) throws Exception {
        return mockMvc.perform(post("/api/v1/cycles/" + cycleId + "/bids")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shareId\":%d,\"amountMinor\":%d}".formatted(shareId, amount)))
                .andReturn();
    }

    @Test
    void hostSubmitsBidAndUpdatesOwnBidLatestWins() throws Exception {
        long groupId = createStartedGroup(hostAToken, 3);
        JsonNode cycle = openCycle(hostAToken, groupId);
        long cycleId = cycle.path("id").asLong();
        long shareId = firstShareOf(hostAToken, groupId).path("id").asLong();

        MvcResult first = submitBid(hostAToken, cycleId, shareId, 200000);
        assertThat(first.getResponse().getStatus()).isEqualTo(201);
        JsonNode firstBody = objectMapper.readTree(first.getResponse().getContentAsString());
        assertThat(firstBody.path("shareId").asLong()).isEqualTo(shareId);
        assertThat(firstBody.path("amountMinor").asLong()).isEqualTo(200000);
        assertThat(firstBody.path("currency").asText()).isEqualTo("VND");
        assertThat(firstBody.path("latest").asBoolean()).isTrue();

        MvcResult update = submitBid(hostAToken, cycleId, shareId, 150000);
        assertThat(update.getResponse().getStatus()).isEqualTo(201);
        JsonNode updated = objectMapper.readTree(update.getResponse().getContentAsString());
        assertThat(updated.path("amountMinor").asLong()).isEqualTo(150000);

        Long rowCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM bids WHERE cycle_id = ? AND share_id = ?", Long.class, cycleId, shareId);
        assertThat(rowCount).isEqualTo(2);
        Long latestCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM bids WHERE cycle_id = ? AND share_id = ? AND is_latest", Long.class, cycleId, shareId);
        assertThat(latestCount).isEqualTo(1);
    }

    @Test
    void invalidBidOutOfRangeOrWrongStepRejected() throws Exception {
        long groupId = createStartedGroup(hostAToken, 3);
        JsonNode cycle = openCycle(hostAToken, groupId);
        long cycleId = cycle.path("id").asLong();
        long shareId = firstShareOf(hostAToken, groupId).path("id").asLong();

        mockMvc.perform(post("/api/v1/cycles/" + cycleId + "/bids")
                        .header("Authorization", "Bearer " + hostAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shareId\":%d,\"amountMinor\":%d}".formatted(shareId, 600000)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/cycles/" + cycleId + "/bids")
                        .header("Authorization", "Bearer " + hostAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shareId\":%d,\"amountMinor\":%d}".formatted(shareId, 25000)))
                .andExpect(status().isBadRequest()); // not multiple of bidStep 10000
    }

    @Test
    void bidNotAllowedOnNonBiddingCycle() throws Exception {
        long groupId = createStartedGroup(hostAToken, 2);
        openCycle(hostAToken, groupId);
        jdbcTemplate.update("UPDATE cycles SET status = 'SETTLED' WHERE group_id = ?", groupId);
        JsonNode lastCycle = openCycle(hostAToken, groupId);
        assertThat(lastCycle.path("status").asText()).isEqualTo("OPEN");
        long shareId = firstShareOf(hostAToken, groupId).path("id").asLong();

        mockMvc.perform(post("/api/v1/cycles/" + lastCycle.path("id").asLong() + "/bids")
                        .header("Authorization", "Bearer " + hostAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shareId\":%d,\"amountMinor\":%d}".formatted(shareId, 100000)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void bidAfterWindowClosedRejected() throws Exception {
        long groupId = createStartedGroup(hostAToken, 3);
        JsonNode cycle = openCycle(hostAToken, groupId);
        long cycleId = cycle.path("id").asLong();
        long shareId = firstShareOf(hostAToken, groupId).path("id").asLong();
        jdbcTemplate.update("UPDATE cycles SET bid_close_at = now() - interval '1 day' WHERE id = ?", cycleId);

        mockMvc.perform(post("/api/v1/cycles/" + cycleId + "/bids")
                        .header("Authorization", "Bearer " + hostAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shareId\":%d,\"amountMinor\":%d}".formatted(shareId, 100000)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deadShareCannotBid() throws Exception {
        long groupId = createStartedGroup(hostAToken, 3);
        JsonNode cycle = openCycle(hostAToken, groupId);
        long cycleId = cycle.path("id").asLong();
        long shareId = firstShareOf(hostAToken, groupId).path("id").asLong();
        jdbcTemplate.update("UPDATE group_shares SET status = 'DEFAULTED' WHERE id = ?", shareId);

        mockMvc.perform(post("/api/v1/cycles/" + cycleId + "/bids")
                        .header("Authorization", "Bearer " + hostAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shareId\":%d,\"amountMinor\":%d}".formatted(shareId, 100000)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void foreignShareBidForbidden() throws Exception {
        long groupA = createStartedGroup(hostAToken, 2);
        long groupB = createStartedGroup(hostBToken, 2);
        JsonNode cycleA = openCycle(hostAToken, groupA);
        JsonNode cycleB = openCycle(hostBToken, groupB);
        long shareInA = firstShareOf(hostAToken, groupA).path("id").asLong();

        mockMvc.perform(post("/api/v1/cycles/" + cycleB.path("id").asLong() + "/bids")
                        .header("Authorization", "Bearer " + hostBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shareId\":%d,\"amountMinor\":%d}".formatted(shareInA, 100000)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/cycles/" + cycleA.path("id").asLong() + "/bids")
                        .header("Authorization", "Bearer " + hostBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shareId\":%d,\"amountMinor\":%d}".formatted(shareInA, 100000)))
                .andExpect(status().isNotFound());
    }

    @Test
    void summaryHidesAmountsBeforeCloseAndRevealsAfter() throws Exception {
        long groupId = createStartedGroup(hostAToken, 3);
        JsonNode cycle = openCycle(hostAToken, groupId);
        long cycleId = cycle.path("id").asLong();
        JsonNode share = firstShareOf(hostAToken, groupId);
        submitBid(hostAToken, cycleId, share.path("id").asLong(), 200000)
                .getResponse();

        MvcResult sealed = mockMvc.perform(get("/api/v1/cycles/" + cycleId + "/summary")
                        .header("Authorization", "Bearer " + hostAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sealed").value(true))
                .andReturn();
        JsonNode sealedBody = objectMapper.readTree(sealed.getResponse().getContentAsString());
        assertThat(sealedBody.path("entries")).isNotEmpty();
        JsonNode entry = sealedBody.path("entries").path(0);
        assertThat(entry.path("bidSubmitted").asBoolean()).isTrue();
        assertThat(entry.path("amountMinor").isNull()).isTrue();
        assertThat(entry.path("submittedAt").isNull()).isTrue();

        jdbcTemplate.update("UPDATE cycles SET bid_close_at = now() - interval '1 day' WHERE id = ?", cycleId);

        MvcResult revealed = mockMvc.perform(get("/api/v1/cycles/" + cycleId + "/summary")
                        .header("Authorization", "Bearer " + hostAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sealed").value(false))
                .andReturn();
        JsonNode revealedBody = objectMapper.readTree(revealed.getResponse().getContentAsString());
        JsonNode revealedEntry = revealedBody.path("entries").path(0);
        assertThat(revealedEntry.path("amountMinor").asLong()).isEqualTo(200000);
        assertThat(revealedEntry.path("submittedAt").asText()).isNotBlank();
    }

    @Test
    void otherHostCannotReadSummary() throws Exception {
        long groupId = createStartedGroup(hostAToken, 2);
        JsonNode cycle = openCycle(hostAToken, groupId);

        mockMvc.perform(get("/api/v1/cycles/" + cycle.path("id").asLong() + "/summary")
                        .header("Authorization", "Bearer " + hostBToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void bidEndpointsRequireAuth() throws Exception {
        mockMvc.perform(post("/api/v1/cycles/1/bids")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/cycles/1/summary")).andExpect(status().isUnauthorized());
    }
}
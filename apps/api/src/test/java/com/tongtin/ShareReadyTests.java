package com.tongtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
class ShareReadyTests {

    private static final String VALID_GROUP = """
            {
              "name": "Hoi Chia 2026",
              "type": "BIDDING",
              "baseAmount": 1000000,
              "shareCount": 3,
              "cycleUnit": "MONTH",
              "cycleCount": 3,
              "currency": "VND",
              "maxBid": 500000,
              "bidStep": 10000
            }
            """;

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
        return "10.10." + ThreadLocalRandom.current().nextInt(256) + "." + ThreadLocalRandom.current().nextInt(256);
    }

    private String registerHost(String phone) throws Exception {
        String body = """
                {
                  "fullName": "Chu Hoi Test",
                  "phone": "%s",
                  "password": "password123",
                  "confirmPassword": "password123",
                  "acceptTerms": true
                }
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

    private long createGroup(String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/groups")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_GROUP))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("id").asLong();
    }

    private long createMember(String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/members")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Thanh Vien\",\"phone\":\"%s\"}".formatted(randomPhone())))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("id").asLong();
    }

    private int assignShares(String token, long groupId, long memberId, int count) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/groups/" + groupId + "/shares")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberProfileId\":%d,\"count\":%d}".formatted(memberId, count)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).size();
    }

    @Test
    void assignSharesUntilFullThenStartReadiesGroupAndFreezesRules() throws Exception {
        long groupId = createGroup(hostAToken);
        long m1 = createMember(hostAToken);
        long m2 = createMember(hostAToken);
        long m3 = createMember(hostAToken);

        assertThat(assignShares(hostAToken, groupId, m1, 1)).isEqualTo(1);
        assertThat(assignShares(hostAToken, groupId, m2, 1)).isEqualTo(2);
        assertThat(assignShares(hostAToken, groupId, m3, 1)).isEqualTo(3);

        mockMvc.perform(post("/api/v1/groups/" + groupId + "/start").header("Authorization", "Bearer " + hostAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.rulesFrozen").value(true));

        mockMvc.perform(patch("/api/v1/groups/" + groupId)
                        .header("Authorization", "Bearer " + hostAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Hack\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cannotStartWhenShareTotalDoesNotEqualN() throws Exception {
        long groupId = createGroup(hostAToken);
        long m1 = createMember(hostAToken);

        assignShares(hostAToken, groupId, m1, 1);

        mockMvc.perform(post("/api/v1/groups/" + groupId + "/start").header("Authorization", "Bearer " + hostAToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void multiShareAllowedAndExceedingNRejected() throws Exception {
        long groupId = createGroup(hostAToken);
        long m1 = createMember(hostAToken);

        mockMvc.perform(post("/api/v1/groups/" + groupId + "/shares")
                        .header("Authorization", "Bearer " + hostAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberProfileId\":%d,\"count\":2}".formatted(m1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(post("/api/v1/groups/" + groupId + "/shares")
                        .header("Authorization", "Bearer " + hostAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberProfileId\":%d,\"count\":2}".formatted(m1)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/groups/" + groupId + "/shares").header("Authorization", "Bearer " + hostAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].shareNo").value(1))
                .andExpect(jsonPath("$[1].shareNo").value(2))
                .andExpect(jsonPath("$[0].status").value("ALIVE"));
    }

    @Test
    void shareOfOtherHostsMemberIsNotFound() throws Exception {
        long groupId = createGroup(hostAToken);
        long foreignMember = createMember(hostBToken);

        mockMvc.perform(post("/api/v1/groups/" + groupId + "/shares")
                        .header("Authorization", "Bearer " + hostAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberProfileId\":%d,\"count\":1}".formatted(foreignMember)))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/groups/" + groupId + "/shares")
                        .header("Authorization", "Bearer " + hostBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberProfileId\":%d,\"count\":1}".formatted(foreignMember)))
                .andExpect(status().isNotFound());
    }

    @Test
    void removeShareReturnsToDraftAndBlocksAfterStart() throws Exception {
        long groupId = createGroup(hostAToken);
        long m1 = createMember(hostAToken);
        long m2 = createMember(hostAToken);
        long m3 = createMember(hostAToken);
        assignShares(hostAToken, groupId, m1, 1);

        MvcResult assign2 = mockMvc.perform(post("/api/v1/groups/" + groupId + "/shares")
                        .header("Authorization", "Bearer " + hostAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberProfileId\":%d,\"count\":1}".formatted(m2)))
                .andExpect(status().isCreated())
                .andReturn();
        long share2Id = objectMapper.readTree(assign2.getResponse().getContentAsString()).path(1).path("id").asLong();

        mockMvc.perform(delete("/api/v1/groups/" + groupId + "/shares/" + share2Id)
                        .header("Authorization", "Bearer " + hostAToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/groups/" + groupId + "/shares").header("Authorization", "Bearer " + hostAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        assignShares(hostAToken, groupId, m2, 1);
        assignShares(hostAToken, groupId, m3, 1);
        mockMvc.perform(post("/api/v1/groups/" + groupId + "/start").header("Authorization", "Bearer " + hostAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"));

        mockMvc.perform(delete("/api/v1/groups/" + groupId + "/shares/1").header("Authorization", "Bearer " + hostAToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shareEndpointsRequireAuth() throws Exception {
        mockMvc.perform(post("/api/v1/groups/1/shares")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberProfileId\":1,\"count\":1}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/groups/1/shares")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/groups/1/start")).andExpect(status().isUnauthorized());
    }
}
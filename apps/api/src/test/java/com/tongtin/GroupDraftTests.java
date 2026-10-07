package com.tongtin;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class GroupDraftTests {

    private static final String VALID_GROUP = """
            {
              "name": "Hoi Nha Tro 2026",
              "type": "BIDDING",
              "baseAmount": 1000000,
              "shareCount": 10,
              "cycleUnit": "MONTH",
              "cycleCount": 10,
              "currency": "VND",
              "startAt": "2026-10-15",
              "maxBid": 900000,
              "minBid": 100000,
              "bidStep": 10000,
              "hostFeeType": "PERCENT_OF_POT",
              "hostFeeBps": 100
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
        return "10.9." + ThreadLocalRandom.current().nextInt(256) + "." + ThreadLocalRandom.current().nextInt(256);
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

    private long createGroup(String token, String payload) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/groups")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("id").asLong();
    }

    @Test
    void hostCanCreateValidBiddingGroupWithGeneratedCode() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/groups")
                        .header("Authorization", "Bearer " + hostAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_GROUP))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.path("code").asText()).matches("HOI-\\d{4}-\\d{3}");
        assertThat(body.path("status").asText()).isEqualTo("DRAFT");
        assertThat(body.path("currency").asText()).isEqualTo("VND");
        assertThat(body.path("baseAmount").asLong()).isEqualTo(1_000_000L);
        assertThat(body.path("createdAt").asText()).isNotBlank();
    }

    @Test
    void codesAreSequentialPerOwner() throws Exception {
        String second = VALID_GROUP.replace("\"Hoi Nha Tro 2026\"", "\"Hoi Hai\"");
        long firstId = createGroup(hostAToken, VALID_GROUP);
        long secondId = createGroup(hostAToken, second);

        JsonNode first = objectMapper.readTree(
                mockMvc.perform(get("/api/v1/groups/" + firstId).header("Authorization", "Bearer " + hostAToken))
                        .andReturn().getResponse().getContentAsString());
        JsonNode secondJson = objectMapper.readTree(
                mockMvc.perform(get("/api/v1/groups/" + secondId).header("Authorization", "Bearer " + hostAToken))
                        .andReturn().getResponse().getContentAsString());
        String firstCode = first.path("code").asText();
        String secondCode = secondJson.path("code").asText();
        assertThat(Integer.parseInt(secondCode.substring(secondCode.lastIndexOf('-') + 1)))
                .isEqualTo(Integer.parseInt(firstCode.substring(firstCode.lastIndexOf('-') + 1)) + 1);
    }

    @Test
    void maxBidAtOrAboveBaseAmountIsRejected() throws Exception {
        String atBase = VALID_GROUP.replace("\"maxBid\": 900000", "\"maxBid\": 1000000");
        mockMvc.perform(post("/api/v1/groups")
                        .header("Authorization", "Bearer " + hostAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(atBase))
                .andExpect(status().isBadRequest());

        String aboveBase = VALID_GROUP.replace("\"maxBid\": 900000", "\"maxBid\": 2000000");
        mockMvc.perform(post("/api/v1/groups")
                        .header("Authorization", "Bearer " + hostAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(aboveBase))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidShareCycleAndCurrencyAreRejected() throws Exception {
        mockMvc.perform(post("/api/v1/groups")
                        .header("Authorization", "Bearer " + hostAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_GROUP.replace("\"shareCount\": 10", "\"shareCount\": 1")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/groups")
                        .header("Authorization", "Bearer " + hostAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_GROUP.replace("\"cycleCount\": 10", "\"cycleCount\": 9")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/groups")
                        .header("Authorization", "Bearer " + hostAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_GROUP.replace("\"currency\": \"VND\"", "\"currency\": \"XXX\"")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/groups")
                        .header("Authorization", "Bearer " + hostAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_GROUP.replace("\"baseAmount\": 1000000", "\"baseAmount\": 0")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void currencyForeignKeyIsEnforcedAtDatabaseLevel() {
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> jdbcTemplate.update(
                "INSERT INTO groups (owner_id, code, name, type, base_amount, share_count, cycle_unit, cycle_count, currency) "
                        + "SELECT id, 'HOI-TEST-999', 'bad', 'FIXED', 1000, 2, 'MONTH', 2, 'XXX' FROM owner_accounts LIMIT 1"))
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void draftCanBeEditedButFrozenGroupCannot() throws Exception {
        long groupId = createGroup(hostAToken, VALID_GROUP);

        mockMvc.perform(patch("/api/v1/groups/" + groupId)
                        .header("Authorization", "Bearer " + hostAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Hoi Sua Ten\",\"baseAmount\":2000000,\"cycleCount\":10,\"maxBid\":1500000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Hoi Sua Ten"))
                .andExpect(jsonPath("$.baseAmount").value(2000000));

        jdbcTemplate.update("UPDATE groups SET status = 'RUNNING', rules_frozen = TRUE WHERE id = ?", groupId);

        mockMvc.perform(patch("/api/v1/groups/" + groupId)
                        .header("Authorization", "Bearer " + hostAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Hack\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidPatchMakingMaxBidHighIsRejected() throws Exception {
        long groupId = createGroup(hostAToken, VALID_GROUP);

        mockMvc.perform(patch("/api/v1/groups/" + groupId)
                        .header("Authorization", "Bearer " + hostAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maxBid\": 1000000}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void otherHostCannotSeeOrEditGroups() throws Exception {
        long groupId = createGroup(hostAToken, VALID_GROUP);

        mockMvc.perform(get("/api/v1/groups").header("Authorization", "Bearer " + hostBToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        mockMvc.perform(get("/api/v1/groups/" + groupId).header("Authorization", "Bearer " + hostBToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch("/api/v1/groups/" + groupId)
                        .header("Authorization", "Bearer " + hostBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Hack\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/groups/" + groupId).header("Authorization", "Bearer " + hostAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Hoi Nha Tro 2026"));
    }

    @Test
    void groupEndpointsRequireHostAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/groups")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_GROUP))
                .andExpect(status().isUnauthorized());
    }
}

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
class MemberDirectoryTests {

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
        jdbcTemplate.update("DELETE FROM member_profiles WHERE owner_id IN (SELECT id FROM owner_accounts WHERE user_id IN (SELECT id FROM users WHERE " + filter + "))");
        jdbcTemplate.update("DELETE FROM owner_accounts WHERE user_id IN (SELECT id FROM users WHERE " + filter + ")");
        jdbcTemplate.update("DELETE FROM user_roles WHERE user_id IN (SELECT id FROM users WHERE " + filter + ")");
        jdbcTemplate.update("DELETE FROM users WHERE " + filter);
    }

    private String ownerFilter() {
        String a = hostAPhone;
        String b = hostBPhone;
        return "phone IN ('" + a + "','" + b + "','+84" + a.substring(1) + "','+84" + b.substring(1) + "')";
    }

    private String uniqueIp() {
        return "10.8." + ThreadLocalRandom.current().nextInt(256) + "." + ThreadLocalRandom.current().nextInt(256);
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

    private MvcResult createMember(String token, String fullName, String phone) throws Exception {
        String body = """
                {"fullName":"%s","phone":"%s","note":"test member"}
                """.formatted(fullName, phone);
        return mockMvc.perform(post("/api/v1/members")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private String randomPhone(String prefix) {
        return prefix + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
    }

    @Test
    void hostCanCreateListSearchAndGetMember() throws Exception {
        String phone = randomPhone("0966");
        MvcResult created = createMember(hostAToken, "Nguyen Van Mot", phone);
        JsonNode body = objectMapper.readTree(created.getResponse().getContentAsString());
        long memberId = body.path("id").asLong();
        assertThat(body.path("phone").asText()).isEqualTo("+84" + phone.substring(1));
        assertThat(body.path("createdAt").asText()).isNotBlank();

        mockMvc.perform(get("/api/v1/members").header("Authorization", "Bearer " + hostAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fullName").value("Nguyen Van Mot"));

        mockMvc.perform(get("/api/v1/members").header("Authorization", "Bearer " + hostAToken)
                        .param("q", "Nguyen Van"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(memberId));

        mockMvc.perform(get("/api/v1/members/" + memberId).header("Authorization", "Bearer " + hostAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Nguyen Van Mot"));
    }

    @Test
    void duplicatePhoneSameOwnerReturns409() throws Exception {
        String phone = randomPhone("0966");
        createMember(hostAToken, "Tran Hai", phone);

        mockMvc.perform(post("/api/v1/members")
                        .header("Authorization", "Bearer " + hostAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Tran Hai Do Cung\",\"phone\":\"%s\"}".formatted(phone)))
                .andExpect(status().isConflict());
    }

    @Test
    void otherHostCannotSeeMembers() throws Exception {
        String phone = randomPhone("0966");
        MvcResult created = createMember(hostAToken, "Le Ba", phone);
        long memberId = objectMapper.readTree(created.getResponse().getContentAsString()).path("id").asLong();

        mockMvc.perform(get("/api/v1/members").header("Authorization", "Bearer " + hostBToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        mockMvc.perform(get("/api/v1/members/" + memberId).header("Authorization", "Bearer " + hostBToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch("/api/v1/members/" + memberId)
                        .header("Authorization", "Bearer " + hostBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Hack\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/members/" + memberId).header("Authorization", "Bearer " + hostBToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/members/" + memberId).header("Authorization", "Bearer " + hostAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Le Ba"));
    }

    @Test
    void updateMemberAndSoftDeactivate() throws Exception {
        String phone = randomPhone("0966");
        String newPhone = randomPhone("0955");
        MvcResult created = createMember(hostAToken, "Pham Tu", phone);
        long memberId = objectMapper.readTree(created.getResponse().getContentAsString()).path("id").asLong();

        mockMvc.perform(patch("/api/v1/members/" + memberId)
                        .header("Authorization", "Bearer " + hostAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Pham Tu Mot\",\"phone\":\"%s\",\"note\":\"updated\"}".formatted(newPhone)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Pham Tu Mot"))
                .andExpect(jsonPath("$.phone").value("+84" + newPhone.substring(1)))
                .andExpect(jsonPath("$.note").value("updated"));

        mockMvc.perform(delete("/api/v1/members/" + memberId).header("Authorization", "Bearer " + hostAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        mockMvc.perform(get("/api/v1/members/" + memberId).header("Authorization", "Bearer " + hostAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    @Test
    void validationRejectsBadMemberPayloads() throws Exception {
        mockMvc.perform(post("/api/v1/members")
                        .header("Authorization", "Bearer " + hostAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"A\",\"phone\":\"0966123456\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/members")
                        .header("Authorization", "Bearer " + hostAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Nguyen Van A\",\"phone\":\"12345\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void memberEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/members")).andExpect(status().isUnauthorized());
    }
}

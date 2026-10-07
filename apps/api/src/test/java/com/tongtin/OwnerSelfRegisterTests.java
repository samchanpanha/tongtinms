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
class OwnerSelfRegisterTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final String TEST_PHONE_FILTER = "(phone LIKE '0999%' OR phone LIKE '+84999%')";

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM audit_events WHERE actor_user_id IN (SELECT id FROM users WHERE " + TEST_PHONE_FILTER + ")");
        jdbcTemplate.update("DELETE FROM owner_accounts WHERE user_id IN (SELECT id FROM users WHERE " + TEST_PHONE_FILTER + ")");
        jdbcTemplate.update("DELETE FROM user_roles WHERE user_id IN (SELECT id FROM users WHERE " + TEST_PHONE_FILTER + ")");
        jdbcTemplate.update("DELETE FROM users WHERE " + TEST_PHONE_FILTER);
    }

    private String randomPhone() {
        return "0999" + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
    }

    private String registerBody(String phone, String password) {
        return """
                {
                  "fullName": "Nguyen Van A",
                  "phone": "%s",
                  "password": "%s",
                  "confirmPassword": "%s",
                  "displayName": "Chu Hoi A",
                  "acceptTerms": true
                }
                """.formatted(phone, password, password);
    }

    private String uniqueIp() {
        return "10.9." + ThreadLocalRandom.current().nextInt(256) + "." + ThreadLocalRandom.current().nextInt(256);
    }

    @Test
    void registerOwnerCreatesHostWithJwt() throws Exception {
        String phone = randomPhone();
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register-owner")
                        .header("X-Forwarded-For", uniqueIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(phone, "password123")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.phone").value("+84" + phone.substring(1)))
                .andExpect(jsonPath("$.owner.displayName").value("Chu Hoi A"))
                .andExpect(jsonPath("$.roles[0]").value("HOST"))
                .andExpect(jsonPath("$.tokens.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokens.refreshToken").isNotEmpty())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        String accessToken = body.path("tokens").path("accessToken").asText();

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("HOST"))
                .andExpect(jsonPath("$.owner.id").isNumber())
                .andExpect(jsonPath("$.onboarding.cccd").value(false));
    }

    @Test
    void duplicatePhoneReturns409() throws Exception {
        String phone = randomPhone();
        mockMvc.perform(post("/api/v1/auth/register-owner")
                        .header("X-Forwarded-For", uniqueIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(phone, "password123")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/register-owner")
                        .header("X-Forwarded-For", uniqueIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(phone, "password456")))
                .andExpect(status().isConflict());
    }

    @Test
    void loginReachesMeWithHostRoleAndOwnerId() throws Exception {
        String phone = randomPhone();
        MvcResult register = mockMvc.perform(post("/api/v1/auth/register-owner")
                        .header("X-Forwarded-For", uniqueIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(phone, "password123")))
                .andExpect(status().isCreated())
                .andReturn();
        long firstOwnerId = objectMapper.readTree(register.getResponse().getContentAsString())
                .path("owner").path("id").asLong();

        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", uniqueIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"%s\",\"password\":\"password123\"}".formatted(phone)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("HOST"))
                .andReturn();

        String accessToken = objectMapper.readTree(login.getResponse().getContentAsString())
                .path("tokens").path("accessToken").asText();

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.owner.id").value(firstOwnerId));
    }

    @Test
    void secondOwnerIsIsolatedFromFirst() throws Exception {
        String phone1 = randomPhone();
        String phone2 = randomPhone();

        MvcResult first = mockMvc.perform(post("/api/v1/auth/register-owner")
                        .header("X-Forwarded-For", uniqueIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(phone1, "password123")))
                .andExpect(status().isCreated())
                .andReturn();
        MvcResult second = mockMvc.perform(post("/api/v1/auth/register-owner")
                        .header("X-Forwarded-For", uniqueIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(phone2, "password456")))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode firstBody = objectMapper.readTree(first.getResponse().getContentAsString());
        JsonNode secondBody = objectMapper.readTree(second.getResponse().getContentAsString());

        assertThat(firstBody.path("owner").path("id").asLong())
                .isNotEqualTo(secondBody.path("owner").path("id").asLong());
        assertThat(firstBody.path("user").path("id").asLong())
                .isNotEqualTo(secondBody.path("user").path("id").asLong());

        String token1 = firstBody.path("tokens").path("accessToken").asText();
        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.owner.id").value(firstBody.path("owner").path("id").asLong()))
                .andExpect(jsonPath("$.owner.id").value(org.hamcrest.Matchers.not(secondBody.path("owner").path("id").asLong())));
    }

    @Test
    void noGenericMemberRegisterEndpoint() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .header("X-Forwarded-For", uniqueIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(randomPhone(), "password123")))
                .andExpect(status().isNotFound());
    }

    @Test
    void passwordIsNeverStoredPlaintext() throws Exception {
        String phone = randomPhone();
        mockMvc.perform(post("/api/v1/auth/register-owner")
                        .header("X-Forwarded-For", uniqueIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(phone, "password123")))
                .andExpect(status().isCreated());

        String hash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM users WHERE phone = ?", String.class, "+84" + phone.substring(1));
        assertThat(hash).isNotEqualTo("password123").startsWith("$2");
    }

    @Test
    void validationRejectsBadRequests() throws Exception {
        String phone = randomPhone();
        mockMvc.perform(post("/api/v1/auth/register-owner")
                        .header("X-Forwarded-For", uniqueIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(phone, "short")))
                .andExpect(status().isBadRequest());

        String mismatch = """
                {
                  "fullName": "Nguyen Van A",
                  "phone": "%s",
                  "password": "password123",
                  "confirmPassword": "password456",
                  "acceptTerms": true
                }
                """.formatted(phone);
        mockMvc.perform(post("/api/v1/auth/register-owner")
                        .header("X-Forwarded-For", uniqueIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mismatch))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/auth/register-owner")
                        .header("X-Forwarded-For", uniqueIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(phone, "password123").replace("\"acceptTerms\": true", "\"acceptTerms\": false")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void wrongPasswordReturns401() throws Exception {
        String phone = randomPhone();
        mockMvc.perform(post("/api/v1/auth/register-owner")
                        .header("X-Forwarded-For", uniqueIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(phone, "password123")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", uniqueIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"%s\",\"password\":\"wrongpassword\"}".formatted(phone)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void patchOwnerProfileUpdatesOnboardingFields() throws Exception {
        String phone = randomPhone();
        MvcResult register = mockMvc.perform(post("/api/v1/auth/register-owner")
                        .header("X-Forwarded-For", uniqueIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(phone, "password123")))
                .andExpect(status().isCreated())
                .andReturn();
        String token = objectMapper.readTree(register.getResponse().getContentAsString())
                .path("tokens").path("accessToken").asText();

        mockMvc.perform(patch("/api/v1/me/owner-profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cccd\":\"012345678901\",\"bankName\":\"Vietcombank\",\"bankAccount\":\"1234567890\",\"accountHolder\":\"Nguyen Van A\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bankName").value("Vietcombank"));

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onboarding.cccd").value(true))
                .andExpect(jsonPath("$.onboarding.bank").value(true));
    }
}
